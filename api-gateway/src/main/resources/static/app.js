'use strict';
(() => {
    const $ = selector => document.querySelector(selector);
    const $$ = selector => Array.from(document.querySelectorAll(selector));
    const state = { token: null, user: null, page: 'home', authMode: 'login', returnTo: 'account', date: dateInMoscow(), type: 'ALL', plans: [], selectedPlan: null, scheduleGeneration: 0, cabinetGeneration: 0 };
    try { state.token = sessionStorage.getItem('sportclub.token'); } catch { /* Memory-only session if storage is disabled. */ }
    let userPromise = null;
    let authBusy = false;
    let planBusy = false;
    let workouts = null;
    let wellness = null;
    let clubTools = null;
    let paymentId = null;
    const types = { STRENGTH: 'Силовая', FUNCTIONAL: 'Функциональная', STRETCH: 'Растяжка' };

    function element(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined && text !== null) node.textContent = String(text);
        return node;
    }
    function button(text, className, action) {
        const node = element('button', className || 'button', text);
        node.type = 'button';
        node.addEventListener('click', action);
        return node;
    }
    function link(text, page, className = 'button') {
        const node = element('a', className, text);
        node.href = '#' + page;
        return node;
    }
    function dateInMoscow(value = new Date()) {
        const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Moscow', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date(value));
        const find = key => parts.find(part => part.type === key).value;
        return `${find('year')}-${find('month')}-${find('day')}`;
    }
    function addDays(date, days) {
        const value = new Date(date + 'T12:00:00+03:00');
        value.setUTCDate(value.getUTCDate() + days);
        return dateInMoscow(value);
    }
    function formattedDate(value, options = {}) {
        return new Intl.DateTimeFormat('ru-RU', { timeZone: 'Europe/Moscow', day: 'numeric', month: 'long', ...options }).format(new Date(value));
    }
    function time(value) { return formattedDate(value, { hour: '2-digit', minute: '2-digit', day: undefined, month: undefined }); }
    function money(value) { return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 }).format(value); }
    function activeMembership(user = state.user) { return Boolean(user?.membership && user.membership.status === 'ACTIVE' && new Date(user.membership.validUntil).getTime() > Date.now()); }
    function notify(text, error = false) {
        const notice = $('#notice');
        notice.textContent = text;
        notice.classList.toggle('is-error', error);
        notice.classList.remove('is-payment-success');
        notice.setAttribute('role', error ? 'alert' : 'status');
        notice.hidden = false;
    }
    function clearNotice() { $('#notice').hidden = true; }
    function setToken(token) {
        state.token = token;
        try { if (token) sessionStorage.setItem('sportclub.token', token); else sessionStorage.removeItem('sportclub.token'); } catch { /* Session stays in memory. */ }
    }
    function clearSession() {
        setToken(null); state.user = null; userPromise = null;
        state.cabinetGeneration++;
        workouts?.clear();
        wellness?.clear();
        $('#account-content').replaceChildren(element('p', 'loading-state', 'Войди, чтобы увидеть свои записи.'));
        updateHeader();
    }
    function updateHeader() {
        const account = $('#account-link');
        account.textContent = state.user ? 'Мой кабинет ↗' : 'Войти ↗';
        account.href = state.user ? '#account' : '#auth';
        $('#logout').hidden = !state.token;
    }
    async function api(path, options = {}) {
        const { anonymous = false, authAttempt = false, ...requestOptions } = options;
        const requestToken = anonymous ? null : state.token;
        const headers = { Accept: 'application/json', ...(requestOptions.body ? { 'Content-Type': 'application/json' } : {}), ...requestOptions.headers };
        if (requestToken) headers.Authorization = 'Bearer ' + requestToken;
        let response;
        try {
            response = await fetch(path, { ...requestOptions, headers, cache: 'no-store', signal: AbortSignal.timeout(12000) });
        } catch (cause) {
            const error = new Error(cause.name === 'TimeoutError' ? 'Ответ занимает больше времени, чем обычно. Попробуй ещё раз.' : 'Не удалось подключиться к сервису. Попробуй ещё раз.');
            error.status = 0; throw error;
        }
        let data = null;
        if (response.status !== 204) {
            const contentType = response.headers.get('content-type') || '';
            if (contentType.includes('json')) { try { data = await response.json(); } catch { } }
        }
        if (!response.ok) {
            const error = new Error(data?.message || (response.status === 401 ? 'Проверь почту и пароль.' : response.status === 403 ? 'Для этого действия нужен действующий абонемент.' : 'Не удалось выполнить действие. Попробуй ещё раз.'));
            error.status = response.status; error.code = data?.code;
            error.fields = data?.fields;
            if (response.status === 401 && !authAttempt && requestToken && state.token === requestToken) {
                const target = state.page === 'auth' ? state.returnTo : state.page;
                clearSession(); state.returnTo = target === 'home' ? 'account' : target;
                go('auth'); notify('Сессия завершилась. Войди снова, чтобы продолжить.');
            }
            throw error;
        }
        return data;
    }
    async function loadUser() {
        if (!state.token) return null;
        const token = state.token;
        if (!userPromise) {
            const request = api('/api/members/me').then(user => { if (state.token !== token) return null; state.user = user; updateHeader(); return user; });
            const tracked = request.finally(() => { if (userPromise === tracked) userPromise = null; });
            userPromise = tracked;
        }
        return userPromise;
    }
    function setLoading(container, message) { container.replaceChildren(element('p', 'loading-state', message)); }
    function errorState(container, error, retry) {
        const box = element('div', 'error-state');
        box.append(element('p', '', error.message));
        if (retry) box.append(button('Попробовать ещё раз', 'button button-outline', retry));
        container.replaceChildren(box);
    }
    function emptyState(title, description, action) {
        const box = element('div', 'empty-state');
        box.append(element('h3', '', title), element('p', '', description));
        if (action) box.append(action);
        return box;
    }
    function go(page) {
        if (location.hash !== '#' + page) history.pushState(null, '', '#' + page);
        showPage(page);
    }
    function showPage(requested) {
        const pages = ['home', 'plans', 'schedule', 'account', 'auth', 'workouts', 'wellness', 'settings', 'staff', 'notices', 'nutrition', 'help'];
        let page = pages.includes(requested) ? requested : 'home';
        if (['account', 'workouts', 'wellness', 'settings', 'staff', 'notices', 'nutrition'].includes(page) && !state.user) { state.returnTo = page; page = 'auth'; }
        if (page === 'auth' && state.user) page = 'account';
        if (page === 'staff' && !['ADMIN', 'TRAINER'].includes(state.user?.role)) page = 'account';
        state.page = page;
        $$('[data-page]').forEach(section => { section.hidden = section.dataset.page !== page; });
        $$('[data-nav]').forEach(nav => { if (nav.dataset.nav === page || (page === 'auth' && nav.dataset.nav === 'account')) nav.setAttribute('aria-current', 'page'); else nav.removeAttribute('aria-current'); });
        const titles = { home: 'Твой клуб. Твой ритм.', plans: 'Абонементы', schedule: 'Расписание', account: 'Мой кабинет', auth: 'Вход и регистрация', workouts: 'Мои тренировки', wellness: 'Мой ритм', settings: 'Настройки профиля', staff: 'Кабинет сотрудника', notices: 'Уведомления', nutrition: 'Моё питание', help: 'Помощь' };
        document.title = 'SportCLUB · ' + titles[page];
        clearNotice();
        if (page === 'home') loadTrainers();
        if (page === 'plans') loadPlans();
        if (page === 'schedule') loadSchedule();
        if (page === 'account') loadCabinet();
        if (page === 'auth') renderAuth();
        if (page === 'workouts') workouts?.open(); else workouts?.leave();
        if (page === 'wellness') wellness?.open(); else wellness?.leave();
        if (page === 'settings') clubTools.settings();
        if (page === 'staff') clubTools.staff();
        if (page === 'notices') clubTools.notices();
        if (page === 'nutrition') clubTools.nutrition();
        if (page === 'help') clubTools.help();
        window.scrollTo({ top: 0, behavior: 'instant' });
    }
    async function loadTrainers() {
        const target = $('#trainers');
        setLoading(target, 'Загружаем тренеров…');
        try {
            const trainers = await api('/api/club/trainers', { anonymous: true });
            if (!Array.isArray(trainers) || !trainers.length) { target.replaceChildren(emptyState('Знакомство скоро состоится', 'Информация о тренерах пока не добавлена.')); return; }
            target.replaceChildren(...trainers.map(trainer => {
                const card = element('article', 'trainer');
                card.append(element('div', 'trainer-avatar', trainer.initials || trainer.name.split(/\s+/).map(part => part[0]).slice(0, 2).join('')),
                    element('h3', '', trainer.name), element('p', 'specialty', trainer.specialty), element('p', '', trainer.bio));
                return card;
            }));
        } catch (error) { errorState(target, error, loadTrainers); }
    }
    async function loadPlans() {
        const target = $('#plans-list');
        setLoading(target, 'Загружаем абонементы…');
        try {
            const plans = await api('/api/members/plans', { anonymous: true });
            state.plans = plans;
            if (!plans.length) { target.replaceChildren(emptyState('Абонементы пока не добавлены', 'Загляни сюда позже.')); return; }
            target.replaceChildren(...plans.map(plan => {
                const isCurrent = activeMembership() && state.user.membership.planId === plan.id;
                const card = element('article', 'plan' + (plan.code?.toLowerCase() === 'rhythm' || plan.id === 'rhythm' ? ' featured' : ''));
                if (isCurrent) card.append(element('span', 'tag tag-active', 'Твой абонемент'));
                else if (plan.code?.toLowerCase() === 'rhythm' || plan.id === 'rhythm') card.append(element('span', 'tag tag-active', 'Для регулярных тренировок'));
                card.append(element('h2', '', plan.name), element('p', 'plan-description', plan.description));
                const price = element('div', 'plan-price', money(plan.priceRub) + ' ₽ ');
                price.append(element('small', '', '/ ' + plan.durationDays + ' дней'));
                card.append(price);
                const list = element('ul');
                (plan.features || []).forEach(feature => list.append(element('li', '', feature)));
                card.append(list);
                const choose = button(isCurrent ? 'Абонемент активен' : 'Выбрать абонемент ↗', 'button ' + (plan.id === 'rhythm' ? 'button-primary' : 'button-outline'), () => choosePlan(plan));
                choose.disabled = isCurrent || activeMembership();
                card.append(choose);
                if (activeMembership() && !isCurrent) card.append(element('p', 'plan-description', 'Новый абонемент можно оформить после окончания текущего.'));
                return card;
            }));
        } catch (error) { errorState(target, error, loadPlans); }
    }
    function choosePlan(plan) {
        if (planBusy) return;
        if (!state.token) { state.returnTo = 'plans'; go('auth'); notify('Войди или создай аккаунт, затем выбери абонемент.'); return; }
        state.selectedPlan = plan;
        paymentId = crypto.randomUUID();
        $('#plan-dialog-title').textContent = plan.name;
        $('#plan-dialog-description').textContent = `${plan.description} Срок действия — ${plan.durationDays} дней. Стоимость тарифа — ${money(plan.priceRub)} ₽.`;
        $('#plan-error').hidden = true;
        $('#confirm-plan').disabled = false;
        $('#confirm-plan').textContent = 'Подтвердить оплату';
        $('#payment-method').value = 'CARD';
        $('#payment-link-card').checked = false;
        $('#payment-card-hint').textContent = '';
        renderPaymentMethod();
        $('#plan-dialog').showModal();
        const pending = paymentId;
        api('/api/members/me/payment-card').then(card => {
            if (pending === paymentId && card.linked && $('#plan-dialog').open) {
                $('#payment-card-hint').textContent = 'Тестовая карта •••• 4242 уже сохранена.';
            }
        }).catch(() => { /* Checkout can continue without the saved-card hint. */ });
    }
    function renderPaymentMethod() {
        const card = $('#payment-method').value === 'CARD';
        $('#payment-link-card').parentElement.hidden = !card;
        $('#payment-card-hint').hidden = !card;
    }
    async function confirmPlan() {
        const plan = state.selectedPlan;
        if (!plan || planBusy) return;
        planBusy = true;
        const submit = $('#confirm-plan');
        submit.disabled = true; submit.textContent = 'Оформляем…';
        $('#close-plan-dialog').disabled = true; $('#dismiss-plan').disabled = true;
        $('#payment-method').disabled = true; $('#payment-link-card').disabled = true;
        $('#plan-error').hidden = true;
        try {
            const method = $('#payment-method').value;
            await api('/api/members/me/payments', { method: 'POST', body: JSON.stringify({
                id: paymentId, planId: plan.id, method, linkCard: method === 'CARD' && $('#payment-link-card').checked
            }) });
            await loadUser();
            $('#plan-dialog').close();
            go('account');
            notify('Учебная оплата прошла. Абонемент активен.');
            $('#notice').classList.add('is-payment-success');
        } catch (error) {
            if (error.status === 401) { $('#plan-dialog').close(); return; }
            $('#plan-error').textContent = error.message;
            $('#plan-error').hidden = false;
        } finally {
            planBusy = false;
            submit.disabled = false; submit.textContent = 'Подтвердить оплату';
            $('#close-plan-dialog').disabled = false; $('#dismiss-plan').disabled = false;
            $('#payment-method').disabled = false; $('#payment-link-card').disabled = false;
        }
    }
    function renderWeek(dates) {
        const today = dates.today;
        const chosenOffset = Math.round((new Date(state.date + 'T12:00:00+03:00') - new Date(today + 'T12:00:00+03:00')) / 86400000);
        const first = chosenOffset >= 0 && chosenOffset < 7 ? today : state.date;
        const days = Array.from({ length: 7 }, (_, index) => addDays(first, index))
            .filter(date => date >= dates.min && date <= dates.max);
        $('#week').replaceChildren(...days.map(date => {
            const full = date + 'T12:00:00+03:00';
            const item = button('', 'date-button', () => { state.date = date; loadSchedule(); });
            item.setAttribute('aria-pressed', String(date === state.date));
            item.setAttribute('aria-label', formattedDate(full, { weekday: 'long' }));
            item.append(element('span', '', formattedDate(full, { weekday: 'short', day: undefined, month: undefined })), element('strong', '', new Date(full).getUTCDate()));
            return item;
        }));
        $('#schedule-date').value = state.date;
        $$('[data-type]').forEach(filter => filter.setAttribute('aria-pressed', String(filter.dataset.type === state.type)));
    }
    async function loadSchedule() {
        const generation = ++state.scheduleGeneration;
        const target = $('#schedule-list');
        setLoading(target, 'Загружаем расписание…');
        try {
            const dates = await clubTools.range();
            if (generation !== state.scheduleGeneration) return;
            const input = $('#schedule-date');
            input.value = state.date;
            state.date = clubTools.applyRange(input, dates);
            renderWeek(dates);
            const query = new URLSearchParams({ date: state.date, type: state.type });
            const classes = await api('/api/club/classes?' + query);
            if (generation !== state.scheduleGeneration) return;
            if (!classes.length) { target.replaceChildren(emptyState('В этот день пока нет занятий', 'Попробуй другую дату или выбери другое направление.')); return; }
            target.replaceChildren(...classes.map(item => {
                const row = element('article', 'class-row class-row--details');
                const times = element('div', '', '');
                times.append(element('strong', 'class-time', time(item.startsAt)), element('span', 'class-end', time(item.endsAt)));
                const details = element('div', 'class-details');
                const title = element('h3');
                title.append(button(item.title, 'text-button class-title-link', () => clubTools.openClass(item.id)));
                details.append(title, element('p', '', `${item.trainerName} · ${item.room}`), element('p', '', types[item.type] || item.type));
                row.addEventListener('click', event => {
                    if (!event.target.closest('button, a')) clubTools.openClass(item.id);
                });
                const actions = element('div', 'class-actions');
                const past = new Date(item.startsAt).getTime() <= Date.now();
                const booked = Boolean(item.bookedByMe);
                const label = booked ? 'Ты записан · в кабинет →' : past ? 'Занятие началось' : item.availableSpots <= 0 ? 'Мест нет' : 'Записаться ↗';
                const action = button(label, 'button ' + (booked ? 'button-outline' : 'button-primary'), () => booked ? go('account') : bookClass(item, action));
                action.disabled = !booked && (past || item.availableSpots <= 0);
                actions.append(action, element('small', '', `Свободно ${item.availableSpots} из ${item.capacity} мест`));
                row.append(times, details, actions);
                return row;
            }));
        } catch (error) { if (generation === state.scheduleGeneration) errorState(target, error, loadSchedule); }
    }
    async function bookClass(item, action) {
        if (!state.token) { state.returnTo = 'schedule'; go('auth'); notify('Войди, чтобы записаться. Выбранный день сохранён.'); return; }
        action.disabled = true; action.textContent = 'Проверяем…';
        try {
            await loadUser();
            if (!activeMembership()) { go('plans'); notify('Для записи нужен действующий абонемент. Выбери подходящий тариф.'); return; }
            await api('/api/club/bookings', { method: 'POST', body: JSON.stringify({ classId: item.id }) });
            await loadSchedule();
            notify('Место забронировано. Запись доступна в личном кабинете.');
        } catch (error) {
            if (error.status === 403) { go('plans'); notify(error.message, true); }
            else if (error.status !== 401) { await loadSchedule(); notify(error.message, true); }
        } finally { if (action.isConnected) { action.disabled = false; action.textContent = 'Записаться ↗'; } }
    }
    async function cancelBooking(booking, action) {
        action.disabled = true; action.textContent = 'Отменяем…';
        try {
            await api('/api/club/bookings/' + encodeURIComponent(booking.id), { method: 'DELETE' });
            await loadCabinet();
            notify('Запись отменена. Место снова доступно в расписании.');
        } catch (error) { if (error.status !== 401) notify(error.message, true); }
        finally { if (action.isConnected) { action.disabled = false; action.textContent = 'Отменить запись'; } }
    }
    async function loadCabinet() {
        if (!state.token) { state.returnTo = 'account'; go('auth'); return; }
        const generation = ++state.cabinetGeneration;
        const target = $('#account-content');
        setLoading(target, 'Загружаем твои записи…');
        try {
            const [user, bookings] = await Promise.all([loadUser(), api('/api/club/me/bookings')]);
            if (generation !== state.cabinetGeneration || !state.token) return;
            $('#greeting').textContent = `Привет, ${user.name.trim().split(/\s+/)[0]}.`;
            $('#account-date').textContent = formattedDate(new Date(), { weekday: 'long' });
            const layout = element('div', 'account-grid');
            const left = element('div');
            const membership = element('article', 'membership-card');
            const active = activeMembership(user);
            membership.append(element('p', 'eyebrow', 'Твой доступ в клуб'), element('h2', '', active ? user.membership.planName : 'Начни свой ритм'));
            if (active) {
                membership.append(element('p', '', 'Абонемент активен. Выбирай удобное время в расписании.'));
                const details = element('div', 'membership-details');
                details.append(element('span', '', 'Действует до'), element('strong', '', formattedDate(user.membership.validUntil, { year: 'numeric' })));
                membership.append(details, link('Найти занятие ↗', 'schedule', 'button'));
            } else {
                membership.append(element('p', '', user.membership ? 'Срок абонемента закончился. Оформи новый, чтобы продолжить занятия.' : 'Абонемента пока нет. Выбери тариф и запишись на первую тренировку.'), link('Выбрать абонемент ↗', 'plans', 'button'));
            }
            const profile = element('div', 'account-profile');
            profile.append(element('h3', '', user.name), element('p', '', user.email));
            const shortcuts = element('div', 'actions');
            shortcuts.append(link('Настройки', 'settings', 'button button-outline'),
                link('Уведомления', 'notices', 'button button-outline'), link('Питание', 'nutrition', 'button button-outline'));
            if (['ADMIN', 'TRAINER'].includes(user.role)) {
                shortcuts.append(link(user.role === 'ADMIN' ? 'Управление расписанием' : 'Кабинет тренера', 'staff', 'button button-primary'));
            }
            profile.append(shortcuts);
            left.append(membership, profile);
            const diary = element('article', 'workout-cabinet-card');
            diary.append(element('p', 'eyebrow', 'Твой тренировочный дневник'), element('h3', '', 'От плана — к результату'), element('p', 'muted', 'Готовые и свои программы, выполненные подходы и история занятий.'), link('Открыть тренировки ↗', 'workouts', 'button button-outline'));
            left.append(diary);
            const metrics = element('article', 'workout-cabinet-card');
            metrics.append(element('p', 'eyebrow', 'Привычки и самочувствие'), element('h3', '', 'Твой ритм каждый день'), element('p', 'muted', 'Отмечай выпитую воду, записывай сон и следи за весом и ИМТ.'), link('Открыть показатели ↗', 'wellness', 'button button-outline'));
            left.append(metrics);
            const right = element('article', 'panel');
            const heading = element('div', 'bookings-heading');
            heading.append(element('h2', '', 'Мои занятия'), link('Расписание →', 'schedule', 'text-link'));
            right.append(heading);
            const actual = bookings.filter(booking => booking.status !== 'CANCELLED').sort((a, b) => new Date(a.startsAt) - new Date(b.startsAt));
            if (!actual.length) right.append(emptyState('Планы пока свободны', 'Выбери занятие, которое хочется посетить. Оно появится здесь после записи.', link('Посмотреть расписание ↗', 'schedule', 'button button-outline')));
            for (const booking of actual) {
                const article = element('div', 'booking');
                const top = element('div', 'booking-heading');
                const date = element('div', 'booking-date', formattedDate(booking.startsAt, { month: 'short' }));
                date.append(element('strong', '', time(booking.startsAt)));
                const name = element('div');
                name.append(element('h3', '', booking.title), element('p', '', `${booking.trainerName} · ${booking.room}`));
                top.append(date, name);
                const bottom = element('div', 'booking-footer');
                const past = new Date(booking.startsAt).getTime() <= Date.now();
                const cancelled = booking.status === 'CLASS_CANCELLED';
                bottom.append(element('span', 'tag' + (past || cancelled ? '' : ' tag-active'),
                    cancelled ? 'Занятие отменено' : past ? 'В истории' : 'Ты записан'));
                if (!past && !cancelled) {
                    const cancel = button('Отменить запись', 'text-button', () => cancelBooking(booking, cancel));
                    bottom.append(cancel);
                }
                article.append(top, bottom); right.append(article);
            }
            layout.append(left, right); target.replaceChildren(layout);
        } catch (error) { if (generation === state.cabinetGeneration && error.status !== 401) errorState(target, error, loadCabinet); }
    }
    function renderAuth() {
        const registering = state.authMode === 'register';
        $$('[data-auth-mode]').forEach(tab => tab.setAttribute('aria-pressed', String(tab.dataset.authMode === state.authMode)));
        $('#name-field').hidden = !registering;
        $('#auth-name').required = registering;
        $('#auth-name').disabled = !registering;
        $('#auth-password').autocomplete = registering ? 'new-password' : 'current-password';
        $('#auth-title').textContent = registering ? 'Знакомство начинается здесь' : 'С возвращением';
        $('#auth-context').textContent = registering ? 'Создай аккаунт, чтобы выбрать абонемент и первое занятие.' : 'Введи почту и пароль своего аккаунта.';
        $('#auth-submit').textContent = registering ? 'Создать аккаунт →' : 'Войти →';
        $('#auth-error').hidden = true;
        $('#fill-demo').disabled = authBusy;
    }
    async function submitAuth(event) {
        event.preventDefault();
        if (authBusy) return;
        const form = $('#auth-form');
        if (!form.reportValidity()) return;
        const registering = state.authMode === 'register';
        const data = { email: $('#auth-email').value.trim(), password: $('#auth-password').value };
        if (registering) data.name = $('#auth-name').value.trim();
        if (registering && data.name.length < 2) { $('#auth-error').textContent = 'Укажи имя: не менее двух символов.'; $('#auth-error').hidden = false; return; }
        authBusy = true;
        $('#auth-submit').disabled = true;
        $('#auth-submit').textContent = registering ? 'Создаём аккаунт…' : 'Входим…';
        $$('[data-auth-mode]').forEach(tab => { tab.disabled = true; });
        $('#fill-demo').disabled = true;
        $('#auth-error').hidden = true;
        try {
            const result = await api('/api/members/auth/' + (registering ? 'register' : 'login'), { method: 'POST', body: JSON.stringify(data), anonymous: true, authAttempt: true });
            setToken(result.token); state.user = result.user;
            await loadUser();
            if (state.token !== result.token) return;
            $('#auth-password').value = '';
            updateHeader();
            const target = state.returnTo;
            state.returnTo = 'account';
            go(target);
            notify(registering ? 'Аккаунт создан. Добро пожаловать в SportCLUB!' : 'С возвращением!');
        } catch (error) { $('#auth-error').textContent = error.message; $('#auth-error').hidden = false; }
        finally {
            authBusy = false; $('#auth-submit').disabled = false;
            $$('[data-auth-mode]').forEach(tab => { tab.disabled = false; });
            $('#fill-demo').disabled = false;
            $('#auth-submit').textContent = state.authMode === 'register' ? 'Создать аккаунт →' : 'Войти →';
        }
    }
    $('#logout').addEventListener('click', () => { clearSession(); go('home'); notify('Ты вышел из аккаунта.'); });
    $('#auth-form').addEventListener('submit', submitAuth);
    $$('[data-auth-mode]').forEach(tab => tab.addEventListener('click', () => { if (!authBusy) { state.authMode = tab.dataset.authMode; renderAuth(); } }));
    $('#fill-demo').addEventListener('click', () => {
        state.authMode = 'login'; renderAuth();
        $('#auth-email').value = 'demo@sportclub.local'; $('#auth-password').value = 'SportClub123!'; $('#auth-submit').focus();
    });
    $('#schedule-date').addEventListener('change', event => { if (event.target.value && event.target.reportValidity()) { state.date = event.target.value; loadSchedule(); } });
    $$('[data-type]').forEach(filter => filter.addEventListener('click', () => { state.type = filter.dataset.type; loadSchedule(); }));
    $('#confirm-plan').addEventListener('click', confirmPlan);
    $('#payment-method').addEventListener('change', renderPaymentMethod);
    ['close-plan-dialog', 'dismiss-plan'].forEach(id => $('#' + id).addEventListener('click', () => { if (!planBusy) $('#plan-dialog').close(); }));
    $('#plan-dialog').addEventListener('cancel', event => { if (planBusy) event.preventDefault(); });
    $('#plan-dialog').addEventListener('click', event => { if (!planBusy && event.target === event.currentTarget) { const box = event.currentTarget.getBoundingClientRect(); if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) event.currentTarget.close(); } });
    $('#year').textContent = new Date().getFullYear();
    workouts = window.SportClubWorkouts({ api, element, button, link, notify, setLoading, errorState, emptyState, formattedDate, go, getToken: () => state.token });
    wellness = window.SportClubWellness({ api, element, button, notify, setLoading, errorState, emptyState, formattedDate, getToken: () => state.token });
    clubTools = window.SportClubTools({ api, element, button, link, notify, errorState, emptyState, formattedDate, time,
        dateInMoscow, addDays, go, getToken: () => state.token, getUser: () => state.user,
        updateSession: result => { setToken(result.token); state.user = { ...state.user, ...result.user }; updateHeader(); } });
    window.addEventListener('hashchange', () => showPage(location.hash.slice(1)));
    updateHeader();
    (async () => {
        if (state.token) {
            try { await loadUser(); } catch (error) { if (error.status !== 401) notify(error.message, true); }
        }
        showPage(location.hash.slice(1));
    })();
})();
