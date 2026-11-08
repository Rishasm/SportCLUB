'use strict';
window.SportClubWorkouts = function (host) {
    const { api, element, button, notify, setLoading, errorState, emptyState, formattedDate, getToken } = host;
    const $ = selector => document.querySelector(selector);
    const $$ = selector => Array.from(document.querySelectorAll(selector));
    const base = '/api/workouts';
    const state = { programs: [], catalog: [], history: [], active: null, tab: 'programs', generation: 0, busy: false, timer: null, visible: false, drafts: new Map(), finishDraft: null, detailGeneration: 0, programRows: [] };
    const number = value => new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(Number(value) || 0);
    const elapsedMinutes = session => Math.max(1, Math.round((Date.now() - new Date(session.startedAt).getTime()) / 60000));
    function current(generation, token) { return generation === state.generation && token && token === getToken(); }
    function stopped(error) { return error.status === 401 || !getToken(); }
    function showTab(name) {
        state.tab = name;
        $$('[data-workout-tab]').forEach(tab => tab.setAttribute('aria-pressed', String(tab.dataset.workoutTab === name)));
        $$('[data-workout-panel]').forEach(panel => { panel.hidden = panel.dataset.workoutPanel !== name; });
        $('#workout-details').hidden = true;
        state.detailGeneration++;
    }
    function clearTimer() { if (state.timer) clearInterval(state.timer); state.timer = null; }
    function leave() { state.visible = false; clearTimer(); }
    function clear() {
        leave(); state.generation++; state.detailGeneration++; state.active = null; state.programs = []; state.catalog = []; state.history = []; state.drafts.clear(); state.finishDraft = null; state.programRows = [];
        ['workout-active', 'workout-programs', 'workout-history', 'workout-catalog', 'workout-details'].forEach(id => $('#' + id).replaceChildren());
        $('#program-dialog').close();
        $('#program-form').reset(); $('#program-exercises').replaceChildren();
    }
    async function open() {
        state.visible = true;
        const generation = ++state.generation, token = getToken();
        setLoading($('#workout-programs'), 'Загружаем программы…');
        setLoading($('#workout-active'), 'Проверяем текущую тренировку…');
        showTab(state.tab);
        try {
            const [programs, catalog, active, history] = await Promise.all([
                api(base + '/programs'), api(base + '/exercises'), api(base + '/sessions/active'), api(base + '/sessions')
            ]);
            if (!current(generation, token)) return;
            if (state.active?.id !== active?.id) { state.drafts.clear(); state.finishDraft = null; }
            Object.assign(state, { programs, catalog, active, history });
            renderPrograms(); renderCatalog(); renderHistory(); renderActive();
        } catch (error) {
            if (current(generation, token) && !stopped(error)) {
                $('#workout-active').replaceChildren();
                for (const id of ['workout-programs', 'workout-history', 'workout-catalog']) errorState($('#' + id), error, open);
            }
        }
    }
    function renderPrograms() {
        const target = $('#workout-programs');
        if (!state.programs.length) { target.replaceChildren(emptyState('Собери свой первый план', 'Выбери упражнения и удобное число подходов.', button('Создать программу +', 'button button-primary', openProgramDialog))); return; }
        target.replaceChildren(...state.programs.map(program => {
            const card = element('article', 'workout-program'); card.dataset.programId = program.id;
            card.append(element('span', 'tag' + (program.isTemplate ? ' tag-active' : ''), program.isTemplate ? 'Готовая программа' : 'Моя программа'), element('h2', '', program.name), element('p', '', program.description || 'Твой персональный набор упражнений.'));
            const exercises = element('ul');
            (program.exercises || []).forEach(exercise => {
                const item = element('li'); item.append(element('span', '', exercise.name), element('small', '', `${exercise.sets} × ${exercise.reps}`)); exercises.append(item);
            });
            const actions = element('div', 'actions');
            const start = button(state.active ? 'Тренировка уже идёт' : 'Начать тренировку ↗', 'button button-primary', () => startProgram(program, start));
            start.dataset.startProgram = program.id; start.disabled = Boolean(state.active) || state.busy;
            actions.append(start);
            if (!program.isTemplate) { const remove = button('Удалить', 'text-button workout-delete', () => deleteProgram(program, remove)); remove.dataset.deleteProgram = program.id; actions.append(remove); }
            card.append(exercises, actions); return card;
        }));
    }
    const exercisePoses = {
        'goblet-squat': [89, 31, 'M87 43L74 70l29 1-9 26m-20-27 14 27m-1-54 12 12 8-8'],
        'dumbbell-bench': [56, 66, 'M68 67h42l15 29m-15-29 7 28m-40-28V38m-10 0h20M45 76h74'],
        'lat-pulldown': [84, 40, 'M84 51v25l20 1v20m-20-46-19-8-5-22m24 30 18-8 5-22M57 21h53'],
        'romanian-deadlift': [120, 51, 'M108 52L78 66l6 31m-6-31-4 31m30-42v32m-14 0h29'],
        'lateral-raise': [84, 31, 'M84 42v31L72 97m12-24 12 24M84 47H48m36 0h36m-72-5v10m72-10v10'],
        'seated-row': [67, 44, 'M68 56l-5 25h36l26 16M68 56l15 13 21-8m0 0h27M53 86h24'],
        'leg-press': [53, 68, 'M62 73l17 13 19-26 23-13m-42 39 24-18 22-13m-11-18 19 23M43 77l36 17'],
        'dumbbell-shoulder-press': [83, 38, 'M83 49v32h26v16M83 51l-16-9V23m16 28 16-9V23M58 23h18m14 0h18M73 85h23'],
        'biceps-curl': [82, 32, 'M82 44v30L73 97m9-23 10 23M82 48l8 22 15-15m-7 0h14'],
        'triceps-pushdown': [79, 33, 'M82 45l-5 29-6 23m6-23 12 23M82 47l11 15 5 18m-8 0h16M98 80V19'],
        'bodyweight-lunge': [81, 32, 'M81 44l4 27 27 2v24M85 71L65 91l-17 6m34-51 9 16'],
        'push-up': [111, 47, 'M104 57L78 68 43 87l-10 10m64-37 14 17v20'],
        'hip-thrust': [52, 66, 'M62 70l31-9 22 7 9 29M62 70l14 12M42 80h35'],
        'crunch': [57, 57, 'M65 65l26 26 21-22 15 28M66 68l12 4 14-8'],
        'calf-raise': [85, 29, 'M85 41v32l-1 14 10 10h11m-20-54 13 19M76 97h35'],
    };

    function exerciseFigure(id) {
        const [x, y, pose] = exercisePoses[id];
        const ns = 'http://www.w3.org/2000/svg';
        const svg = document.createElementNS(ns, 'svg');
        svg.setAttribute('class', 'exercise-figure');
        svg.setAttribute('viewBox', '0 0 168 112');
        svg.setAttribute('aria-hidden', 'true');
        const floor = document.createElementNS(ns, 'path');
        floor.setAttribute('d', 'M25 99h118');
        floor.setAttribute('stroke', '#53664e');
        const body = document.createElementNS(ns, 'path');
        body.setAttribute('d', pose);
        body.setAttribute('fill', 'none');
        body.setAttribute('stroke', '#b5ff7a');
        body.setAttribute('stroke-width', '4');
        body.setAttribute('stroke-linecap', 'round');
        body.setAttribute('stroke-linejoin', 'round');
        const head = document.createElementNS(ns, 'circle');
        head.setAttribute('cx', x);
        head.setAttribute('cy', y);
        head.setAttribute('r', '8');
        head.setAttribute('fill', '#d9ebd0');
        svg.append(floor, body, head);
        return svg;
    }

    function catalogVideo(source, title) {
        const video = element('video', 'exercise-video');
        video.controls = true;
        video.playsInline = true;
        video.preload = 'metadata';
        video.src = source;
        video.setAttribute('aria-label', title);
        return video;
    }

    function renderCatalog() {
        const target = $('#workout-catalog');
        target.replaceChildren(...state.catalog.map(exercise => {
            const card = element('article', 'exercise-catalog-card');
            if (exercisePoses[exercise.id]) card.append(exerciseFigure(exercise.id));
            card.append(element('span', 'tag', exercise.muscleGroup), element('h3', '', exercise.name), element('p', '', exercise.description), element('p', 'equipment', exercise.equipment));
            if (exercise.id === 'push-up') card.append(catalogVideo(window.SportClubPhotos.exerciseVideosByType.STRENGTH, 'Техника отжиманий'));
            return card;
        }));
        if (!state.catalog.length) target.append(emptyState('Каталог пока пуст', 'Упражнения появятся после добавления в клубе.'));
        else {
            const stretching = element('article', 'exercise-catalog-card');
            stretching.append(element('h3', '', 'Растяжка'), catalogVideo(window.SportClubPhotos.exerciseVideosByType.STRETCH, 'Техника растяжки'));
            target.append(stretching);
        }
    }
    function renderHistory() {
        const target = $('#workout-history');
        if (!state.history.length) { target.replaceChildren(emptyState('Первая тренировка впереди', 'Заверши тренировку — здесь появятся подходы, объём и время занятия.')); return; }
        target.replaceChildren(...state.history.map(session => {
            const item = element('article', 'workout-history-item'); item.dataset.sessionId = session.id;
            const text = element('div');
            text.append(element('h3', '', session.programName), element('p', '', `${formattedDate(session.finishedAt, { year: 'numeric' })} · ${session.durationMinutes} мин · ${session.completedSets} из ${session.totalSets} подходов`));
            const details = button('Посмотреть результат →', 'button button-outline', () => openHistory(session.id)); details.dataset.sessionDetails = session.id;
            item.append(text, details); return item;
        }));
    }
    function clock() {
        const node = $('#workout-elapsed');
        if (!node || !state.active) return;
        const seconds = Math.max(0, Math.floor((Date.now() - new Date(state.active.startedAt).getTime()) / 1000));
        node.textContent = `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
    }
    function renderActive() {
        const target = $('#workout-active'); target.replaceChildren(); clearTimer();
        const session = state.active;
        if (!session) return;
        const heading = element('div', 'workout-session-head');
        const top = element('div', 'workout-session-heading');
        const title = element('div'); title.append(element('p', 'eyebrow', 'Текущая тренировка'), element('h2', '', session.programName));
        const duration = element('div', 'workout-clock'); const elapsed = element('span'); elapsed.id = 'workout-elapsed'; duration.append(elapsed, element('small', '', 'с начала тренировки'));
        top.append(title, duration);
        const progress = element('div', 'workout-session-progress'); progress.setAttribute('role', 'progressbar'); progress.setAttribute('aria-label', 'Выполненные подходы'); progress.setAttribute('aria-valuemin', '0'); progress.setAttribute('aria-valuemax', String(session.totalSets)); progress.setAttribute('aria-valuenow', String(session.completedSets));
        const fill = element('span'); fill.style.width = `${session.totalSets ? session.completedSets / session.totalSets * 100 : 0}%`; progress.append(fill);
        const status = element('div', 'workout-session-status'); status.append(element('span', '', `${session.completedSets} из ${session.totalSets} подходов сохранено`), element('span', '', `Объём: ${number(session.totalVolumeKg)} кг`));
        heading.append(top, progress, status);
        const layout = element('div', 'workout-active-layout'), exercises = element('div', 'workout-exercises');
        for (const exercise of session.exercises) {
            const panel = element('article', 'workout-exercise'); panel.append(element('h3', '', exercise.name));
            const previous = exercise.previousResult;
            panel.append(element('p', 'previous-result', previous ? `В прошлый раз: ${number(previous.weightKg)} кг × ${previous.reps} · ${formattedDate(previous.completedAt)}` : 'Первый результат для этого упражнения.'));
            const head = element('div', 'workout-set-head'); ['Подход', 'Вес, кг', 'Повторы', 'Готово', ''].forEach(text => head.append(element('span', '', text))); panel.append(head);
            for (const set of exercise.sets) panel.append(setForm(session, exercise, set));
            exercises.append(panel);
        }
        layout.append(exercises, finishPanel(session));
        target.append(heading, layout);
        clock(); if (state.visible) state.timer = setInterval(clock, 1000);
    }
    function labeledInput(type, name, value, label, options = {}) {
        const wrapper = element('label'); const input = element('input'); input.type = type; input.name = name; input.value = value ?? ''; input.setAttribute('aria-label', label);
        Object.entries(options).forEach(([key, value]) => { input[key] = value; }); wrapper.append(input); return { wrapper, input };
    }
    function setForm(session, exercise, set) {
        const form = element('form', 'workout-set'); form.dataset.setId = set.id; form.dataset.completed = String(set.completed);
        const draft = state.drafts.get(set.id) || set;
        const weight = labeledInput('number', 'weightKg', draft.weightKg, `${exercise.name}, подход ${set.setNumber}, вес в килограммах`, { min: '0', max: '500', step: '0.01', required: true });
        const reps = labeledInput('number', 'reps', draft.reps, `${exercise.name}, подход ${set.setNumber}, повторения`, { min: '1', max: '100', step: '1', required: true });
        const check = element('label', 'workout-check'), checkbox = element('input'); checkbox.type = 'checkbox'; checkbox.name = 'completed'; checkbox.checked = Boolean(draft.completed); checkbox.setAttribute('aria-label', `${exercise.name}, подход ${set.setNumber}, выполнен`); check.append(checkbox);
        const save = element('button', 'button button-outline', set.completed ? 'Обновить' : 'Сохранить'); save.type = 'submit'; save.dataset.saveSet = set.id;
        const error = element('p', 'workout-set-error'); error.setAttribute('role', 'alert'); error.hidden = true;
        form.append(element('span', 'set-number', set.setNumber), weight.wrapper, reps.wrapper, check, save, error);
        form.addEventListener('input', () => { state.drafts.set(set.id, { weightKg: weight.input.value, reps: reps.input.value, completed: checkbox.checked }); });
        form.addEventListener('change', () => { state.drafts.set(set.id, { weightKg: weight.input.value, reps: reps.input.value, completed: checkbox.checked }); });
        form.addEventListener('submit', async event => {
            event.preventDefault(); if (state.busy || !form.reportValidity()) return;
            const token = getToken(), generation = state.generation;
            state.busy = true; setSessionControls(true); save.textContent = 'Сохраняем…'; error.hidden = true;
            try {
                const updated = await api(`${base}/sessions/${session.id}/sets/${set.id}`, { method: 'PUT', body: JSON.stringify({ weightKg: Number(weight.input.value), reps: Number(reps.input.value), completed: checkbox.checked }) });
                if (!current(generation, token)) return;
                state.drafts.delete(set.id); state.active = updated; renderActive();
                const savedForm = $$('#workout-active .workout-set').find(item => item.dataset.setId === set.id);
                savedForm?.querySelector('[data-save-set]')?.focus({ preventScroll: true });
            } catch (problem) { if (current(generation, token) && !stopped(problem)) { error.textContent = problem.message; error.hidden = false; } }
            finally { state.busy = false; setSessionControls(false); if (save.isConnected) save.textContent = set.completed ? 'Обновить' : 'Сохранить'; }
        });
        return form;
    }
    function setSessionControls(disabled) { $$('#workout-active input, #workout-active button').forEach(control => { control.disabled = disabled; }); if (!disabled && $('#finish-workout')) $('#finish-workout').disabled = !state.active?.completedSets; }
    function finishPanel(session) {
        if (state.finishDraft?.sessionId !== session.id) state.finishDraft = { sessionId: session.id, durationMinutes: null, weightKg: '' };
        const panel = element('aside', 'workout-finish'); panel.append(element('h3', '', 'Хорошая работа.'), element('p', '', 'Отметь выполненные подходы и нажми «Сохранить» в каждой строке. Перед завершением проверь длительность занятия.'));
        const suggestedDuration = elapsedMinutes(session);
        if (suggestedDuration > 360) panel.append(element('p', 'workout-subnote', 'Тренировка открыта больше шести часов. Укажи фактическую длительность занятия перед завершением.'));
        const form = element('form'); form.id = 'workout-finish-form';
        const durationLabel = element('label', '', 'Длительность, мин'), duration = element('input'); duration.type = 'number'; duration.name = 'durationMinutes'; duration.min = '1'; duration.max = '360'; duration.step = '1'; duration.value = state.finishDraft.durationMinutes ?? (suggestedDuration > 360 ? '' : suggestedDuration); duration.required = true; durationLabel.append(duration);
        const weightLabel = element('label', '', 'Вес тела, кг — необязательно'), weight = element('input'); weight.type = 'number'; weight.name = 'weightKg'; weight.min = '30'; weight.max = '300'; weight.step = '0.1'; weight.placeholder = 'Для оценки калорий'; weight.value = state.finishDraft.weightKg; weightLabel.append(weight);
        duration.addEventListener('input', () => { state.finishDraft.durationMinutes = duration.value; });
        weight.addEventListener('input', () => { state.finishDraft.weightKg = weight.value; });
        const error = element('p', 'form-error'); error.setAttribute('role', 'alert'); error.hidden = true;
        const actions = element('div', 'finish-actions'); const finish = element('button', 'button button-primary', 'Завершить тренировку'); finish.type = 'submit'; finish.id = 'finish-workout'; finish.disabled = session.completedSets === 0;
        const discard = button('Отменить эту тренировку', 'text-button workout-delete', () => discardSession(session)); discard.id = 'discard-workout';
        actions.append(finish, discard); form.append(durationLabel, weightLabel, error, actions);
        form.addEventListener('submit', async event => {
            event.preventDefault(); if (state.busy || !form.reportValidity()) return;
            if (state.drafts.size) { error.textContent = 'Сначала сохрани изменённые подходы: в историю попадут только сохранённые значения.'; error.hidden = false; return; }
            const data = { durationMinutes: Number(duration.value) }; if (weight.value !== '') data.weightKg = Number(weight.value);
            const token = getToken(), generation = state.generation; state.busy = true; setSessionControls(true); finish.textContent = 'Завершаем…'; error.hidden = true;
            try {
                const completed = await api(`${base}/sessions/${session.id}/finish`, { method: 'POST', body: JSON.stringify(data) });
                if (!current(generation, token)) return;
                state.active = null; state.drafts.clear(); state.finishDraft = null; clearTimer(); $('#workout-active').replaceChildren();
                state.history = [completed, ...state.history.filter(item => item.id !== completed.id)];
                renderHistory(); renderPrograms(); showTab('history'); showDetails(completed); notify('Тренировка сохранена в истории. Отличный шаг вперёд!');
            } catch (problem) { if (current(generation, token) && !stopped(problem)) { error.textContent = problem.message; error.hidden = false; } }
            finally { state.busy = false; setSessionControls(false); if (finish.isConnected) finish.textContent = 'Завершить тренировку'; renderPrograms(); }
        });
        panel.append(form, element('p', '', 'Расход калорий рассчитывается приблизительно, если указан вес. Фактическая нагрузка может отличаться.'));
        return panel;
    }
    async function startProgram(program, control) {
        if (state.busy || state.active) return;
        const token = getToken(), generation = state.generation; state.busy = true; control.disabled = true; control.textContent = 'Начинаем…';
        try {
            const session = await api(base + '/sessions', { method: 'POST', body: JSON.stringify({ programId: program.id }) });
            if (!current(generation, token)) return;
            state.active = session; state.drafts.clear(); state.finishDraft = null; renderActive(); renderPrograms(); $('#workout-active').scrollIntoView({ behavior: 'smooth', block: 'start' }); notify('Тренировка началась. Записывай и сохраняй каждый подход.');
        } catch (error) { if (current(generation, token) && !stopped(error)) { notify(error.message, true); if (error.status === 409) await open(); } }
        finally { state.busy = false; renderPrograms(); }
    }
    async function discardSession(session) {
        if (state.busy || !window.confirm('Отменить текущую тренировку? Её подходы не сохранятся в истории.')) return;
        const token = getToken(), generation = state.generation; state.busy = true; setSessionControls(true);
        try {
            await api(`${base}/sessions/${session.id}`, { method: 'DELETE' }); if (!current(generation, token)) return;
            state.active = null; state.drafts.clear(); state.finishDraft = null; renderActive(); notify('Текущая тренировка отменена. Можно начать новую.');
        } catch (error) { if (current(generation, token) && !stopped(error)) notify(error.message, true); }
        finally { state.busy = false; setSessionControls(false); renderPrograms(); }
    }
    async function deleteProgram(program, control) {
        if (state.busy || !window.confirm(`Удалить программу «${program.name}»? Завершённые тренировки останутся в истории.`)) return;
        const token = getToken(), generation = state.generation; state.busy = true; control.disabled = true;
        try {
            await api(`${base}/programs/${program.id}`, { method: 'DELETE' }); if (!current(generation, token)) return;
            state.programs = state.programs.filter(item => item.id !== program.id); notify('Программа удалена. История тренировок сохранена.');
        } catch (error) { if (current(generation, token) && !stopped(error)) notify(error.message, true); }
        finally { state.busy = false; renderPrograms(); }
    }
    async function openHistory(id) {
        const generation = ++state.detailGeneration, token = getToken(); $('#workout-details').hidden = false; setLoading($('#workout-details'), 'Загружаем результат…');
        try { const session = await api(`${base}/sessions/${id}`); if (generation === state.detailGeneration && token === getToken()) showDetails(session); }
        catch (error) { if (generation === state.detailGeneration && token === getToken() && !stopped(error)) errorState($('#workout-details'), error, () => openHistory(id)); }
    }
    function showDetails(session) {
        const target = $('#workout-details'), card = element('article', 'workout-summary');
        const heading = element('div', 'section-heading'); heading.style.marginTop = '0'; heading.append(element('h2', '', session.programName), button('Скрыть результат', 'text-button', () => { target.hidden = true; }));
        card.append(element('p', 'eyebrow', 'Тренировка завершена'), heading, element('p', 'muted', formattedDate(session.finishedAt || session.startedAt, { year: 'numeric' })));
        const metrics = element('div', 'summary-metrics');
        const values = [[`${session.durationMinutes} мин`, 'Длительность'], [`${session.completedSets} / ${session.totalSets}`, 'Подходы выполнены'], [`${number(session.totalVolumeKg)} кг`, 'Объём выполненных подходов']];
        if (session.estimatedCalories !== null && session.estimatedCalories !== undefined) values.push([`≈ ${number(session.estimatedCalories)} ккал`, 'Приблизительный расход']);
        values.forEach(([value, label]) => { const metric = element('div', 'summary-metric'); metric.append(element('strong', '', value), element('small', '', label)); metrics.append(metric); }); card.append(metrics);
        if (session.estimatedCalories !== null && session.estimatedCalories !== undefined) card.append(element('p', 'muted', 'Расчётная оценка по длительности и весу. Она не является точным измерением расхода энергии.'));
        for (const exercise of session.exercises || []) {
            const item = element('div', 'workout-detail-exercise'); item.append(element('h3', '', exercise.name));
            exercise.sets.forEach(set => { const line = element('div', 'workout-detail-set'); line.append(element('span', '', `Подход ${set.setNumber}`), element('span', '', set.completed ? `${number(set.weightKg)} кг × ${set.reps}` : 'Не выполнен')); item.append(line); }); card.append(item);
        }
        target.replaceChildren(card); target.hidden = false;
        if (state.visible) target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
    function openProgramDialog() {
        if (state.busy || !state.catalog.length) { if (!state.catalog.length) notify('Сначала дождись загрузки каталога упражнений.', true); return; }
        $('#program-form').reset(); $('#program-error').hidden = true; state.programRows = [];
        addProgramRow(); $('#program-dialog').showModal(); $('#program-name').focus();
    }
    function addProgramRow() {
        if (state.programRows.length >= 12) return;
        const available = state.catalog.find(exercise => !state.programRows.some(row => row.exerciseId === exercise.id));
        state.programRows.push({ exerciseId: available?.id || state.catalog[0]?.id || '', sets: '3', reps: '10', weightKg: '0' }); renderProgramRows();
    }
    function renderProgramRows() {
        const target = $('#program-exercises');
        target.replaceChildren(...state.programRows.map((row, index) => {
            const card = element('div', 'program-exercise-row');
            const heading = element('div', 'program-row-head'); heading.append(element('span', '', `Упражнение ${index + 1}`), button('Убрать', 'text-button', () => { state.programRows.splice(index, 1); renderProgramRows(); }));
            const label = element('label', '', 'Упражнение'), select = element('select'); select.name = 'exerciseId'; select.required = true; select.setAttribute('aria-label', `Упражнение ${index + 1}`);
            state.catalog.forEach(exercise => { const option = element('option', '', exercise.name); option.value = exercise.id; option.selected = exercise.id === row.exerciseId; select.append(option); });
            select.addEventListener('change', () => { row.exerciseId = select.value; }); label.append(select);
            const values = element('div', 'program-row-values');
            [['sets', 'Подходы', 1, 6, 1], ['reps', 'Повторы', 1, 100, 1], ['weightKg', 'Вес, кг', 0, 500, .01]].forEach(([key, text, min, max, step]) => {
                const field = element('label', '', text), input = element('input'); input.type = 'number'; input.name = key; input.min = min; input.max = max; input.step = step; input.value = row[key]; input.required = true; input.setAttribute('aria-label', `Упражнение ${index + 1}, ${text.toLowerCase()}`); input.addEventListener('input', () => { row[key] = input.value; }); field.append(input); values.append(field);
            }); card.append(heading, label, values); return card;
        }));
        $('#add-program-exercise').disabled = state.programRows.length >= 12;
    }
    async function saveProgram(event) {
        event.preventDefault(); if (state.busy || !$('#program-form').reportValidity()) return;
        const error = $('#program-error'); error.hidden = true;
        const name = $('#program-name').value.trim();
        if (name.length < 2 || !state.programRows.length) { error.textContent = 'Укажи название и добавь хотя бы одно упражнение.'; error.hidden = false; return; }
        if (new Set(state.programRows.map(row => row.exerciseId)).size !== state.programRows.length) { error.textContent = 'В программе каждое упражнение должно встречаться один раз.'; error.hidden = false; return; }
        const body = { name, description: $('#program-description').value.trim(), exercises: state.programRows.map(row => ({ exerciseId: row.exerciseId, sets: Number(row.sets), reps: Number(row.reps), weightKg: Number(row.weightKg) })) };
        const token = getToken(), generation = state.generation; state.busy = true; setProgramControls(true); $('#save-program').textContent = 'Сохраняем…';
        try {
            const program = await api(base + '/programs', { method: 'POST', body: JSON.stringify(body) }); if (!current(generation, token)) return;
            state.programs.push(program); $('#program-dialog').close(); showTab('programs'); renderPrograms(); notify('Твоя программа сохранена. Можно начинать тренировку.');
        } catch (problem) { if (current(generation, token) && !stopped(problem)) { error.textContent = problem.message; error.hidden = false; } }
        finally { state.busy = false; setProgramControls(false); $('#save-program').textContent = 'Сохранить программу'; renderPrograms(); }
    }
    function setProgramControls(disabled) { $$('#program-dialog input, #program-dialog textarea, #program-dialog select, #program-dialog button').forEach(control => { control.disabled = disabled; }); if (!disabled) $('#add-program-exercise').disabled = state.programRows.length >= 12; }
    $$('[data-workout-tab]').forEach(tab => tab.addEventListener('click', () => showTab(tab.dataset.workoutTab)));
    $('#create-program').addEventListener('click', openProgramDialog);
    $('#close-program-dialog').addEventListener('click', () => { if (!state.busy) $('#program-dialog').close(); });
    $('#program-dialog').addEventListener('cancel', event => { if (state.busy) event.preventDefault(); });
    $('#program-form').addEventListener('submit', saveProgram);
    $('#add-program-exercise').addEventListener('click', addProgramRow);
    return { open, clear, leave };
};
