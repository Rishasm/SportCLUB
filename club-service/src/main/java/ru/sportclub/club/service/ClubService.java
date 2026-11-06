package ru.sportclub.club.service;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.sportclub.foundation.ApiException;
import ru.sportclub.club.client.MembershipAccessClient;
import ru.sportclub.club.entity.ClassBooking;
import ru.sportclub.club.entity.ClubClass;
import ru.sportclub.club.entity.ClubTrainer;
import ru.sportclub.club.repository.ClassBookingRepository;
import ru.sportclub.club.repository.ClubClassRepository;
import ru.sportclub.club.repository.ClubTrainerRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static ru.sportclub.club.dto.ClubModels.*;

@Service
public class ClubService {
    public static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final Set<String> TYPES = Set.of("ALL", "STRENGTH", "FUNCTIONAL", "STRETCH");
    private static final String CONFIRMED = "CONFIRMED";

    private final ClubTrainerRepository trainers;
    private final ClubClassRepository classes;
    private final ClassBookingRepository bookings;
    private final TransactionTemplate transactions;
    private final MembershipAccessClient membership;
    private final JdbcTemplate jdbc;
    private final LocalDate openedOn;
    private final int scheduleDays;

    public ClubService(ClubTrainerRepository trainers, ClubClassRepository classes, ClassBookingRepository bookings,
                       TransactionTemplate transactions, MembershipAccessClient membership, JdbcTemplate jdbc,
                       @Value("${sportclub.club.opened-on:2026-01-01}") LocalDate openedOn,
                       @Value("${sportclub.club.schedule-days:30}") int scheduleDays) {
        this.trainers = trainers;
        this.classes = classes;
        this.bookings = bookings;
        this.transactions = transactions;
        this.membership = membership;
        this.jdbc = jdbc;
        this.openedOn = openedOn;
        this.scheduleDays = scheduleDays;
    }

    public List<Trainer> trainers() {
        return trainers.findAllByOrderByNameAsc().stream().map(ClubService::trainerView).toList();
    }

    public CalendarRange calendar() {
        LocalDate today = LocalDate.now(MOSCOW);
        return new CalendarRange(openedOn, today.plusDays(scheduleDays), today);
    }

    private void checkDate(LocalDate date) {
        var range = calendar();
        if (date.isBefore(range.min()) || date.isAfter(range.max())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_OUT_OF_RANGE",
                    "Выбери дату между " + range.min() + " и " + range.max() + ".");
        }
    }

    @Transactional(readOnly = true)
    public List<ClassView> classes(LocalDate date, String type, UUID memberId) {
        checkDate(date);
        String normalized = type.toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CLASS_TYPE", "Неизвестное направление занятий.");
        }
        var from = date.atStartOfDay(MOSCOW).toOffsetDateTime();
        var until = date.plusDays(1).atStartOfDay(MOSCOW).toOffsetDateTime();
        var schedule = classes.findSchedule(from, until, normalized.equals("ALL") ? null : normalized);
        if (schedule.isEmpty()) return List.of();

        List<UUID> ids = schedule.stream().map(ClubClass::getId).toList();
        var occupied = bookings.countConfirmedByClassIds(ids).stream().collect(Collectors.toMap(
                ClassBookingRepository.Occupancy::getClassId, ClassBookingRepository.Occupancy::getOccupied));
        Set<UUID> mine = memberId == null ? Set.of() : bookings.findBookedClassIds(memberId, ids);
        return schedule.stream().map(c -> classView(c, occupied.getOrDefault(c.getId(), 0L),
                mine.contains(c.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public ClassDetails details(UUID id, UUID memberId) {
        ClubClass item = classes.findById(id).orElseThrow(() -> notFound("Занятие не найдено."));
        boolean booked = memberId != null && bookings.findByClubClass_IdAndMemberId(id, memberId)
                .filter(b -> CONFIRMED.equals(b.getStatus())).isPresent();
        return details(item, booked);
    }

    @Transactional(readOnly = true)
    public List<ClassDetails> staffClasses(LocalDate date, UUID trainerAccount) {
        checkDate(date);
        var from = date.atStartOfDay(MOSCOW).toOffsetDateTime();
        var until = from.plusDays(1);
        var items = trainerAccount == null
                ? classes.findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(from, until)
                : classes.findByTrainer_IdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                        trainerId(trainerAccount), from, until);
        return items.stream().map(item -> details(item, false)).toList();
    }

    @Transactional
    public ClassDetails saveClass(UUID id, ClassEdit request) {
        ClubClass item = id == null ? null : lockClass(id);
        if (item != null) requireEditable(item);
        var start = localTime(request.startsAt());
        var end = start.plusMinutes(request.durationMinutes());
        checkDate(start.toLocalDate());
        if (!start.isAfter(OffsetDateTime.now()) || !start.toLocalDate().equals(end.toLocalDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CLASS_TIME", "Занятие должно пройти в будущем в пределах одного дня.");
        }
        var trainer = trainers.findByIdForUpdate(request.trainerId())
                .orElseThrow(() -> notFound("Тренер не найден."));
        UUID classId = id == null ? UUID.randomUUID() : id;
        if (classes.existsByTrainer_IdAndCancelledFalseAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
                trainer.getId(), classId, end, start)) {
            throw conflict("TRAINER_BUSY", "У тренера уже есть занятие в это время.");
        }
        var participants = item == null ? List.<ClassBooking>of() : confirmed(id);
        if (participants.size() > request.capacity()) {
            throw conflict("CAPACITY_TOO_LOW", "Вместимость меньше числа подтверждённых записей.");
        }
        String title = request.title().trim();
        String room = request.room().trim();
        if (item == null) {
            item = new ClubClass(classId, title, request.type().name(), trainer, room, start, end, request.capacity());
        } else if (!item.getStartsAt().isEqual(start) || !item.getEndsAt().isEqual(end)
                || !item.getRoom().equals(room) || !item.getTrainer().getId().equals(trainer.getId())
                || !item.getTitle().equals(title)) {
            for (var participant : participants) {
                insertNotice(participant.getMemberId(), "Занятие изменено",
                        title + " · " + start.toLocalDate() + " " + start.toLocalTime() + " (МСК) · " + room);
            }
        }
        item.update(title, request.type().name(), trainer, room, start, end, request.capacity(), request.level().name());
        item.updateContent(request.description().trim(), request.technique().trim());
        return details(classes.save(item), false);
    }

    @Transactional
    public void removeClass(UUID id) {
        ClubClass item = lockClass(id);
        if (item.isCancelled()) return;
        requireEditable(item);
        if (!item.isDemo() && !bookings.existsByClubClass_Id(id)) {
            classes.delete(item);
            return;
        }
        item.cancel();
        for (var participant : confirmed(id)) {
            insertNotice(participant.getMemberId(), "Занятие отменено",
                    item.getTitle() + " · " + localTime(item.getStartsAt()).toLocalDate());
        }
    }

    @Transactional(readOnly = true)
    public List<Participant> participants(UUID accountId, UUID classId) {
        requireAssigned(accountId, classes.findById(classId).orElseThrow(() -> notFound("Занятие не найдено.")));
        return confirmed(classId).stream().map(b -> new Participant(b.getMemberId(), b.getMemberName())).toList();
    }

    @Transactional
    public ClassDetails updateContent(UUID accountId, UUID classId, ClassContent request) {
        ClubClass item = lockClass(classId);
        requireAssigned(accountId, item);
        requireEditable(item);
        item.updateContent(request.description().trim(), request.technique().trim());
        return details(item, false);
    }

    public List<Notice> notices(UUID memberId) {
        return jdbc.query("""
                select id, title, body, created_at, member_id is null as announcement
                from club_notices where member_id = ? or member_id is null order by created_at desc, id limit 50
                """, (row, index) -> new Notice(row.getObject("id", UUID.class), row.getString("title"),
                row.getString("body"), localTime(row.getObject("created_at", OffsetDateTime.class)),
                row.getBoolean("announcement")), memberId);
    }

    public Notice announce(Announcement request) {
        return insertNotice(null, request.title().trim(), request.body().trim());
    }

    private Notice insertNotice(UUID memberId, String title, String body) {
        Notice notice = new Notice(UUID.randomUUID(), title, body, OffsetDateTime.now(), memberId == null);
        jdbc.update("insert into club_notices (id, member_id, title, body, created_at) values (?, ?, ?, ?, ?)",
                notice.id(), memberId, title, body, notice.createdAt());
        return notice;
    }

    private ClubClass lockClass(UUID id) {
        return classes.findByIdForUpdate(id).orElseThrow(() -> notFound("Занятие не найдено."));
    }

    private List<ClassBooking> confirmed(UUID classId) {
        return bookings.findAllByClubClass_IdAndStatusOrderByBookedAt(classId, CONFIRMED);
    }

    private static void requireEditable(ClubClass item) {
        if (item.isCancelled() || !item.getStartsAt().isAfter(OffsetDateTime.now())) {
            throw conflict("CLASS_CLOSED", "Занятие уже началось или отменено.");
        }
    }

    private UUID trainerId(UUID accountId) {
        return trainers.findByAccountId(accountId).map(ClubTrainer::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "TRAINER_NOT_ASSIGNED",
                        "Для аккаунта не назначен профиль тренера."));
    }

    private void requireAssigned(UUID accountId, ClubClass item) {
        if (!trainerId(accountId).equals(item.getTrainer().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_YOUR_CLASS", "Это занятие назначено другому тренеру.");
        }
    }

    private ClassDetails details(ClubClass item, boolean booked) {
        long occupied = bookings.countByClubClass_IdAndStatus(item.getId(), CONFIRMED);
        return new ClassDetails(classView(item, occupied, booked), trainerView(item.getTrainer()),
                item.getDescription(), item.getTechnique(), item.getLevel(), item.isCancelled(),
                item.isDemo() && "STRENGTH".equals(item.getType()));
    }

    private static ClassView classView(ClubClass item, long occupied, boolean booked) {
        return new ClassView(item.getId(), item.getTitle(), item.getType(), item.getTrainer().getId(),
                item.getTrainer().getName(), item.getRoom(), localTime(item.getStartsAt()), localTime(item.getEndsAt()),
                item.getCapacity(), (int) Math.max(0, item.getCapacity() - occupied), booked);
    }

    private static Trainer trainerView(ClubTrainer trainer) {
        return new Trainer(trainer.getId(), trainer.getName(), trainer.getSpecialty(), trainer.getBio(), trainer.getInitials());
    }

    @Transactional(readOnly = true)
    public List<Booking> myBookings(UUID memberId) {
        return bookings.findAllByMemberIdAndStatusOrderByClubClass_StartsAt(memberId, CONFIRMED).stream()
                .map(ClubService::bookingView).toList();
    }

    public Booking book(UUID memberId, String memberName, String bearerToken, UUID classId) {
        // Check membership before locking a local row, so the network call holds no database lock.
        membership.requireActiveMembership(memberId, bearerToken);
        return transactions.execute(transaction -> {
            ClubClass clubClass = classes.findByIdForUpdate(classId)
                    .orElseThrow(() -> notFound("Занятие не найдено."));
            if (clubClass.isCancelled()) throw conflict("CLASS_CANCELLED", "Занятие отменено.");
            checkDate(localTime(clubClass.getStartsAt()).toLocalDate());
            if (!clubClass.getStartsAt().isAfter(OffsetDateTime.now())) {
                throw conflict("CLASS_ALREADY_STARTED", "Запись на начавшееся занятие закрыта.");
            }
            ClassBooking previous = bookings.findByClubClass_IdAndMemberId(classId, memberId).orElse(null);
            if (previous != null && CONFIRMED.equals(previous.getStatus())) {
                throw conflict("ALREADY_BOOKED", "Вы уже записаны на это занятие.");
            }
            if (bookings.countByClubClass_IdAndStatus(classId, CONFIRMED) >= clubClass.getCapacity()) {
                throw conflict("CLASS_FULL", "Свободных мест больше нет. Выберите другое занятие.");
            }
            OffsetDateTime now = OffsetDateTime.now();
            if (previous != null) {
                previous.confirm(now);
                previous.setMemberName(memberName);
                return bookingView(previous);
            }
            ClassBooking booking = new ClassBooking(clubClass, memberId, now);
            booking.setMemberName(memberName);
            return bookingView(bookings.save(booking));
        });
    }

    @Transactional
    public void cancel(UUID memberId, UUID bookingId) {
        UUID classId = bookings.findOwnedClassId(bookingId, memberId)
                .orElseThrow(() -> notFound("Запись не найдена."));
        // Bookings and cancellations serialize on the same class row.
        classes.findByIdForUpdate(classId).orElseThrow(() -> notFound("Занятие не найдено."));
        bookings.findByIdAndMemberId(bookingId, memberId)
                .orElseThrow(() -> notFound("Запись не найдена.")).cancel();
    }

    private static Booking bookingView(ClassBooking booking) {
        ClubClass c = booking.getClubClass();
        return new Booking(booking.getId(), c.getId(), c.getTitle(), c.getTrainer().getName(), c.getRoom(),
                localTime(c.getStartsAt()), localTime(c.getEndsAt()), c.isCancelled() ? "CLASS_CANCELLED" : booking.getStatus());
    }

    private static OffsetDateTime localTime(OffsetDateTime time) {
        return time.atZoneSameInstant(MOSCOW).toOffsetDateTime();
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
