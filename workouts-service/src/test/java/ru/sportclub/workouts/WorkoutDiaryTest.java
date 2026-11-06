package ru.sportclub.workouts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.sportclub.foundation.TokenService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.sportclub.workouts.dto.WorkoutModels.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "spring.config.location=classpath:/application-test.yml", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "sportclub.service-title=Тренировки",
        "sportclub.api-prefix=/api/workouts", "sportclub.configuration-source=test", "sportclub.brand.name=SportCLUB",
        "sportclub.brand.accent=#B5FF7A", "sportclub.brand.card-radius=24"
})
class WorkoutDiaryTest {
    @DynamicPropertySource
    static void postgresSchema(DynamicPropertyRegistry properties) {
        String schema = "test_" + java.util.UUID.randomUUID().toString().replace("-", "");
        properties.add("sportclub.test.schema", () -> schema);
    }

    @Autowired TestRestTemplate http;
    @Autowired TokenService tokens;
    @Autowired JdbcTemplate jdbc;
    private static final UUID TEMPLATE = UUID.fromString("a1111111-1111-4111-8111-111111111111");

    @Test
    void catalogAndDiaryRequireAuthentication() {
        assertThat(http.getForEntity("/api/workouts/exercises", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String token = token();
        var exercises = get("/exercises", token, Exercise[].class);
        assertThat(exercises.getBody()).hasSize(15);
        var programs = get("/programs", token, Program[].class);
        assertThat(programs.getBody()).hasSize(2).allMatch(Program::isTemplate);
    }

    @Test
    void validatesProgramAndNumericSetBoundaries() {
        String token = token();
        var invalid = new ProgramRequest("Тест", "", List.of(new ProgramExerciseRequest("missing", 1, 10, BigDecimal.ZERO)));
        assertThat(post("/programs", token, invalid, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var duplicates = new ProgramRequest("Тест", "", List.of(exercise(1), exercise(1)));
        assertThat(post("/programs", token, duplicates, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var program = ownProgram(token, 2);
        var active = start(token, program.id());
        var set = active.exercises().get(0).sets().get(0);
        assertThat(put("/sessions/"+active.id()+"/sets/"+set.id(), token,
                new SetRequest(new BigDecimal("-1"), 10, true), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(put("/sessions/"+active.id()+"/sets/"+set.id(), token,
                new SetRequest(BigDecimal.ZERO, 101, true), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/sessions/"+active.id()+"/finish", token,
                new FinishRequest(0, BigDecimal.valueOf(80)), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createsOwnProgramsAndProtectsEveryForeignOperation() {
        String owner = token(), other = token();
        var program = ownProgram(owner, 2);
        assertThat(get("/programs", other, Program[].class).getBody()).noneMatch(p -> p.id().equals(program.id()));
        assertThat(post("/sessions", other, new StartRequest(program.id()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete("/programs/"+program.id(), other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var active = start(owner, program.id());
        var set = active.exercises().get(0).sets().get(0);
        assertThat(get("/sessions/"+active.id(), other, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(put("/sessions/"+active.id()+"/sets/"+set.id(), other, new SetRequest(BigDecimal.TEN, 10, true), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/sessions/"+active.id()+"/finish", other, new FinishRequest(30, null), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete("/sessions/"+active.id(), other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/sessions", other, SessionSummary[].class).getBody()).isEmpty();
    }

    @Test
    void snapshotsProgramAndPersistsCompletedResultsAndCalorieEstimate() {
        String token = token();
        Program program = ownProgram(token, 2);
        Session active = start(token, program.id());
        assertThat(active.totalSets()).isEqualTo(2);
        assertThat(post("/sessions", token, new StartRequest(TEMPLATE), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var first = active.exercises().get(0).sets().get(0);
        var updated = put("/sessions/"+active.id()+"/sets/"+first.id(), token,
                new SetRequest(BigDecimal.valueOf(20), 10, true), Session.class).getBody();
        assertThat(updated.completedSets()).isEqualTo(1);
        assertThat(updated.totalVolumeKg()).isEqualByComparingTo("200");
        assertThat(delete("/programs/"+program.id(), token).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        var finished = post("/sessions/"+active.id()+"/finish", token,
                new FinishRequest(30, BigDecimal.valueOf(80)), Session.class).getBody();
        assertThat(finished.status()).isEqualTo("COMPLETED");
        assertThat(finished.durationMinutes()).isEqualTo(30);
        assertThat(finished.estimatedCalories()).isEqualTo(147);
        assertThat(finished.programName()).isEqualTo(program.name());
        assertThat(finished.exercises()).hasSize(1);
        assertThat(finished.exercises().get(0).sets()).hasSize(2);
        assertThat(get("/sessions", token, SessionSummary[].class).getBody()).singleElement()
                .satisfies(summary -> assertThat(summary.id()).isEqualTo(finished.id()));
        assertThat(put("/sessions/"+active.id()+"/sets/"+first.id(), token,
                new SetRequest(BigDecimal.TEN, 5, true), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(delete("/sessions/"+active.id(), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("/sessions/"+active.id()+"/finish", token, new FinishRequest(5,null), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void previousResultUsesOnlyLastCompletedSetFromThisMembersHistory() {
        String token = token(), other = token();
        Program program = ownProgram(token, 3);
        Session active = start(token, program.id());
        List<WorkoutSet> sets = active.exercises().get(0).sets();
        put("/sessions/"+active.id()+"/sets/"+sets.get(0).id(), token, new SetRequest(BigDecimal.valueOf(20),10,true), Session.class);
        put("/sessions/"+active.id()+"/sets/"+sets.get(1).id(), token, new SetRequest(BigDecimal.valueOf(25),8,true), Session.class);
        put("/sessions/"+active.id()+"/sets/"+sets.get(2).id(), token, new SetRequest(BigDecimal.valueOf(99),99,false), Session.class);
        Session completed = post("/sessions/"+active.id()+"/finish", token, new FinishRequest(25,null), Session.class).getBody();
        assertThat(completed.estimatedCalories()).isNull();
        assertThat(completed.exercises().get(0).previousResult()).isNull();
        Session next = start(token, program.id());
        PreviousResult previous = next.exercises().get(0).previousResult();
        assertThat(previous.weightKg()).isEqualByComparingTo("25");
        assertThat(previous.reps()).isEqualTo(8);
        assertThat(previous.completedAt().toInstant()).isEqualTo(completed.finishedAt().toInstant());
        Session otherSession = start(other, TEMPLATE);
        assertThat(otherSession.exercises()).allMatch(ex -> ex.previousResult() == null);
    }

    @Test
    void emptyFinishIsRejectedAndDiscardAllowsNewWorkout() {
        String token = token();
        Session active = start(token, TEMPLATE);
        assertThat(post("/sessions/"+active.id()+"/finish", token, new FinishRequest(null,null), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(delete("/sessions/"+active.id(), token).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/sessions/"+active.id(), token, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(start(token, TEMPLATE).status()).isEqualTo("ACTIVE");
        assertThat(delete("/programs/"+TEMPLATE, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aSetCannotBeAddressedThroughAnotherSession() {
        String first = token(), second = token();
        Session one = start(first, TEMPLATE), two = start(second, TEMPLATE);
        var foreignSet = two.exercises().get(0).sets().get(0);
        assertThat(put("/sessions/"+one.id()+"/sets/"+foreignSet.id(), first,
                new SetRequest(BigDecimal.TEN, 10, true), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aLongRunningSessionRequiresAnExplicitActualDuration() {
        String token = token();
        Session active = start(token, TEMPLATE);
        WorkoutSet set = active.exercises().get(0).sets().get(0);
        put("/sessions/"+active.id()+"/sets/"+set.id(), token, new SetRequest(BigDecimal.TEN,10,true), Session.class);
        jdbc.update("UPDATE workout_sessions SET started_at = ? WHERE id = ?",
                java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusHours(7), active.id());
        var rejected = post("/sessions/"+active.id()+"/finish", token, new FinishRequest(null,null), Map.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().get("code")).isEqualTo("DURATION_REQUIRED");
        assertThat(get("/sessions/"+active.id(),token,Session.class).getBody().status()).isEqualTo("ACTIVE");
        var finished = post("/sessions/"+active.id()+"/finish", token, new FinishRequest(30,null), Session.class);
        assertThat(finished.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(finished.getBody().durationMinutes()).isEqualTo(30);
    }

    @Test
    void concurrentStartCreatesExactlyOneActiveSession() throws Exception {
        UUID member = UUID.randomUUID();
        String token = tokens.issue(member,"MEMBER","concurrent@test.local");
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<HttpStatusCode> task = () -> {
                ready.countDown();
                if (!go.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("No start signal");
                return post("/sessions",token,new StartRequest(TEMPLATE),String.class).getStatusCode();
            };
            var one = executor.submit(task); var two = executor.submit(task);
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(List.of(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(HttpStatus.CREATED,HttpStatus.CONFLICT);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workout_sessions WHERE member_id = ? AND status = 'ACTIVE'", Integer.class,member)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    private String token() { UUID id=UUID.randomUUID(); return tokens.issue(id,"MEMBER",id+"@test.local"); }
    private ProgramExerciseRequest exercise(int sets) { return new ProgramExerciseRequest("goblet-squat",sets,10,BigDecimal.ZERO); }
    private Program ownProgram(String token,int sets) {
        var response=post("/programs",token,new ProgramRequest("Моя программа","Личный план",List.of(exercise(sets))),Program.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);return response.getBody();
    }
    private Session start(String token,UUID id) {
        var response=post("/sessions",token,new StartRequest(id),Session.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);return response.getBody();
    }
    private HttpEntity<Object> auth(String token,Object body) { var headers=new HttpHeaders();headers.setBearerAuth(token);return new HttpEntity<>(body,headers); }
    private <T> ResponseEntity<T> get(String path,String token,Class<T> type) {return http.exchange("/api/workouts"+path,HttpMethod.GET,auth(token,null),type);}
    private <T> ResponseEntity<T> post(String path,String token,Object body,Class<T> type) {return http.exchange("/api/workouts"+path,HttpMethod.POST,auth(token,body),type);}
    private <T> ResponseEntity<T> put(String path,String token,Object body,Class<T> type) {return http.exchange("/api/workouts"+path,HttpMethod.PUT,auth(token,body),type);}
    private ResponseEntity<String> delete(String path,String token) {return http.exchange("/api/workouts"+path,HttpMethod.DELETE,auth(token,null),String.class);}
}
