package ru.sportclub.members.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportclub.foundation.ApiException;
import ru.sportclub.foundation.TokenService;
import ru.sportclub.members.entity.MemberAccount;
import ru.sportclub.members.entity.MemberMembership;
import ru.sportclub.members.entity.MembershipPlan;
import ru.sportclub.members.repository.MemberAccountRepository;
import ru.sportclub.members.repository.MemberMembershipRepository;
import ru.sportclub.members.repository.MembershipPlanRepository;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static ru.sportclub.members.dto.MembersDtos.*;

@Service
public class MembersService {
    private final MemberAccountRepository accounts;
    private final MembershipPlanRepository plans;
    private final MemberMembershipRepository memberships;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final JdbcTemplate jdbc;
    private static final String ADMIN_EMAIL = "admin@sportclub.local";
    private static final String TRAINER_EMAIL = "trainer@sportclub.local";
    private final String dummyPasswordHash;

    public MembersService(MemberAccountRepository accounts, MembershipPlanRepository plans,
                          MemberMembershipRepository memberships, PasswordEncoder passwords, TokenService tokens,
                          JdbcTemplate jdbc) {
        this.accounts = accounts;
        this.plans = plans;
        this.memberships = memberships;
        this.passwords = passwords;
        this.tokens = tokens;
        this.jdbc = jdbc;
        this.dummyPasswordHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String name = request.name().trim();
        if (name.length() < 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NAME", "Укажи имя длиной от 2 до 80 символов.");
        }
        validatePasswordLength(request.password());
        MemberAccount account = new MemberAccount(UUID.randomUUID(), name, allowedEmail(request.email()),
                passwords.encode(request.password()), "MEMBER");
        saveProfile(account);
        return authenticated(userView(account));
    }

    private void saveProfile(MemberAccount account) {
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            if (!isDuplicateKey(exception)) throw exception;
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Аккаунт с таким email уже существует.");
        }
    }

    public AuthResponse login(LoginRequest request) {
        MemberAccount account = accounts.findByEmail(normalizeEmail(request.email())).orElse(null);
        String hash = account == null ? dummyPasswordHash : account.getPasswordHash();
        boolean validLength = request.password().getBytes(StandardCharsets.UTF_8).length <= 72;
        boolean matches = validLength && passwords.matches(request.password(), hash);
        if (account == null || !matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Неверный email или пароль.");
        }
        return authenticated(userView(account));
    }

    @Transactional(readOnly = true)
    public Profile profile(UUID memberId) {
        User user = userView(requireAccount(memberId));
        return new Profile(user.id(), user.name(), user.email(), user.role(), latestMembership(memberId));
    }

    @Transactional
    public AuthResponse updateProfile(UUID id, ProfileRequest request) {
        MemberAccount account = lockedAccount(id);
        String name = request.name().trim();
        if (name.length() < 2) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NAME", "Укажи имя от двух символов.");
        String email = normalizeEmail(request.email());
        if (!email.equals(account.getEmail())) email = allowedEmail(email);
        account.updateProfile(name, email);
        saveProfile(account);
        return authenticated(userView(account));
    }

    @Transactional
    public void changePassword(UUID id, PasswordRequest request) {
        MemberAccount account = lockedAccount(id);
        validatePasswordLength(request.currentPassword());
        validatePasswordLength(request.newPassword());
        if (!passwords.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "Проверь текущий пароль.");
        }
        account.changePassword(passwords.encode(request.newPassword()));
    }

    public List<Plan> plans() {
        return plans.findAllByOrderByPriceRubAscIdAsc().stream().map(plan -> new Plan(
                plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(), plan.getPriceRub(),
                plan.getDurationDays(), List.of(plan.getFeatures().split("\\|")))).toList();
    }

    @Transactional
    public Membership activate(UUID memberId, String planId) {
        return checkout(memberId, new PaymentRequest(UUID.randomUUID(), planId, PaymentMethod.CARD, false)).membership();
    }

    @Transactional
    public PaymentResult checkout(UUID memberId, PaymentRequest request) {
        if (request.linkCard() && request.method() != PaymentMethod.CARD) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CARD_OPTION", "Карту можно сохранить при выборе оплаты картой.");
        }
        MemberAccount account = lockedAccount(memberId);
        var previous = jdbc.query("""
                select p.method, p.amount_rub, p.membership_id
                from demo_payments p where p.id = ? and p.member_id = ?
                """, (rs, row) -> new PaymentResult(request.id(), PaymentMethod.valueOf(rs.getString(1)), rs.getInt(2),
                "SUCCEEDED", membershipView(memberships.findById(rs.getObject(3, UUID.class)).orElseThrow())),
                request.id(), memberId);
        if (!previous.isEmpty()) {
            PaymentResult saved = previous.get(0);
            if (saved.method() != request.method() || !saved.membership().planId().equals(request.planId())) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_ID_REUSED", "Начни новое оформление.");
            }
            return saved;
        }
        Membership current = latestMembership(memberId);
        if (current != null && "ACTIVE".equals(current.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMBERSHIP_ACTIVE", "У тебя уже есть действующий абонемент.");
        }
        MembershipPlan plan = plans.findById(request.planId()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Абонемент не найден."));
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC);
        MemberMembership membership = memberships.saveAndFlush(new MemberMembership(account, plan, start,
                start.plusDays(plan.getDurationDays())));
        int inserted = jdbc.update("""
                insert into demo_payments(id, member_id, membership_id, method, amount_rub, paid_at)
                values (?, ?, ?, ?, ?, ?) on conflict (id) do nothing
                """, request.id(), memberId, membership.getId(), request.method().name(), plan.getPriceRub(), start);
        if (inserted == 0) throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_ID_REUSED", "Начни новое оформление.");
        if (request.linkCard()) linkTestCard(memberId);
        return new PaymentResult(request.id(), request.method(), plan.getPriceRub(), "SUCCEEDED", membershipView(membership));
    }

    public SavedCard savedCard(UUID id) {
        requireAccount(id);
        boolean linked = Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from demo_payment_methods where member_id = ?)", Boolean.class, id));
        return new SavedCard(linked, linked ? "4242" : null);
    }

    @Transactional
    public SavedCard setCard(UUID id, boolean link) {
        lockedAccount(id);
        if (link) linkTestCard(id);
        else jdbc.update("delete from demo_payment_methods where member_id = ?", id);
        return new SavedCard(link, link ? "4242" : null);
    }

    private void linkTestCard(UUID id) {
        jdbc.update("insert into demo_payment_methods(member_id) values (?) on conflict do nothing", id);
    }

    @Transactional(readOnly = true)
    public Access access(UUID memberId) {
        requireAccount(memberId);
        Membership membership = latestMembership(memberId);
        return new Access(memberId, membership != null && "ACTIVE".equals(membership.status()),
                membership == null ? null : membership.validUntil());
    }

    public void ensureDemoAccount() {
        ensureDemo("11111111-1111-4111-8111-111111111111", "Александр", "demo@sportclub.local", "SportClub123!", "MEMBER");
        ensureDemo("22222222-2222-4222-8222-222222222222", "Администратор клуба", ADMIN_EMAIL, "SportClubAdmin26!", "ADMIN");
        ensureDemo("33333333-3333-4333-8333-333333333333", "Алексей Смирнов", TRAINER_EMAIL, "SportClubTrainer26!", "TRAINER");
    }

    private void ensureDemo(String stableId, String name, String email, String password, String role) {
        UUID id = UUID.fromString(stableId);
        if (accounts.existsById(id) || accounts.existsByEmail(email)) return;
        try {
            accounts.saveAndFlush(new MemberAccount(id, name, email, passwords.encode(password), role));
        } catch (DataIntegrityViolationException exception) {
            if (!isDuplicateKey(exception) || !(accounts.existsById(id) || accounts.existsByEmail(email))) throw exception;
        }
    }

    private MemberAccount lockedAccount(UUID id) {
        return accounts.findByIdForUpdate(id).orElseThrow(MembersService::accountNotFound);
    }

    private Membership latestMembership(UUID memberId) {
        return memberships.findFirstByMember_IdOrderByValidUntilDescStartsAtDesc(memberId)
                .map(MembersService::membershipView).orElse(null);
    }

    private static Membership membershipView(MemberMembership membership) {
        OffsetDateTime start = membership.getStartsAt();
        OffsetDateTime end = membership.getValidUntil();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String status = !start.isAfter(now) && end.isAfter(now) ? "ACTIVE" : "EXPIRED";
        return new Membership(membership.getId(), membership.getPlan().getId(), membership.getPlan().getName(),
                start, end, status);
    }

    private MemberAccount requireAccount(UUID memberId) {
        return accounts.findById(memberId).orElseThrow(MembersService::accountNotFound);
    }

    private static ApiException accountNotFound() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_NOT_FOUND", "Аккаунт не найден. Войди снова.");
    }

    private static User userView(MemberAccount account) {
        return new User(account.getId(), account.getName(), account.getEmail(), account.getRole());
    }

    private AuthResponse authenticated(User user) {
        return new AuthResponse(tokens.issue(user.id(), user.role(), user.email(), user.name()), TokenService.EXPIRES_IN, user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String allowedEmail(String email) {
        String normalized = normalizeEmail(email);
        if (ADMIN_EMAIL.equals(normalized) || TRAINER_EMAIL.equals(normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Этот email зарезервирован.");
        }
        return normalized;
    }

    private void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG", "Пароль должен занимать не больше 72 байт в UTF-8.");
        }
    }

    private static boolean isDuplicateKey(DataIntegrityViolationException exception) {
        return exception.getMostSpecificCause() instanceof SQLException sql && "23505".equals(sql.getSQLState());
    }
}
