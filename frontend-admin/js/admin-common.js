const API = (window.location.port === '8084' || window.location.port === '81') && window.location.protocol !== 'file:' ? '' : 'http://localhost:8089';

/* ========== 多语言（与用户端共用 localStorage('lang')，跨端跨页面口径一致） ========== */
const ADMIN_LANG_LABEL = { zh: '中文', en: 'English', ja: '日本語' };
let adminLang = localStorage.getItem('lang') || 'zh';

/** 给请求路径附加当前语言参数，让后台列表与用户端列表/详情按同一语言口径返回。 */
function withLang(path) {
    if (!adminLang || adminLang === 'zh') return path;
    // 已显式指定 lang（如编辑时强制读取中文原文 lang=zh）时不覆盖
    if (/[?&]lang=/.test(path)) return path;
    return path + (path.includes('?') ? '&' : '?') + 'lang=' + adminLang;
}

async function api(path) {
    const res = await fetch(API + withLang(path), { credentials: 'include' });
    const data = await res.json();
    if (data.code === 401) { showToast('请先登录', 'error'); setTimeout(() => location.href = 'login.html', 1000); return null; }
    if (data.code !== 200) { showToast(data.msg || '操作失败', 'error'); return null; }
    return (data.data !== null && data.data !== undefined) ? data.data : true;
}

/**
 * 服务端在某字段缺少译文回退到中文时，会通过 fallbackFields 标记。
 * 非中文环境下为该字段渲染一个小徽章，提示当前展示的是中文回退内容。
 */
function fallbackBadge(obj, field) {
    if (adminLang === 'zh' || !obj || !obj.fallbackFields || !obj.fallbackFields[field]) return '';
    const tip = adminLang === 'en' ? 'No translation yet · showing Chinese' : '翻訳が未登録のため中国語を表示中';
    const label = adminLang === 'en' ? 'ZH' : '中文';
    return ' <span class="lang-fallback-tag" title="' + tip + '">' + label + '</span>';
}

function showConfirm(msg, onOk) {
    const id = 'cfm' + Date.now();
    const div = document.createElement('div');
    div.className = 'confirm-overlay';
    div.id = id;
    div.innerHTML =
        '<div class="confirm-box">' +
            '<div class="confirm-icon-wrap"><i class="fas fa-exclamation-circle"></i></div>' +
            '<div class="confirm-text">' + msg + '</div>' +
            '<div class="confirm-btns">' +
                '<button class="confirm-btn-cancel" id="' + id + 'c">取消</button>' +
                '<button class="confirm-btn-ok" id="' + id + 'k">确认</button>' +
            '</div>' +
        '</div>';
    document.body.appendChild(div);
    document.getElementById(id + 'c').onclick = () => div.remove();
    document.getElementById(id + 'k').onclick = () => { div.remove(); onOk && onOk(); };
    div.onclick = (e) => { if (e.target === div) div.remove(); };
}

function getParam(key) {
    return new URLSearchParams(location.search).get(key);
}

function saveUser(u) {
    localStorage.setItem('user', JSON.stringify(u));
}

function getUser() {
    try { return JSON.parse(localStorage.getItem('user')); } catch { return null; }
}

function clearUser() {
    localStorage.removeItem('user');
}

function showToast(msg, type = 'success') {
    let c = document.querySelector('.toast-container');
    if (!c) { c = document.createElement('div'); c.className = 'toast-container'; document.body.appendChild(c); }
    const t = document.createElement('div');
    t.className = 'toast toast-' + type;
    t.textContent = msg;
    c.appendChild(t);
    setTimeout(() => { t.style.opacity = '0'; t.style.transition = 'opacity .3s'; setTimeout(() => t.remove(), 300); }, 2500);
}

function imgError(el) {
    el.src = 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="60" height="40" fill="%23ccc"><rect width="60" height="40" fill="%23f5f5f5"/><text x="50%" y="55%" text-anchor="middle" font-size="10" fill="%23ccc">暂无图</text></svg>';
}

function renderPagination(containerId, current, total, callback) {
    const c = document.getElementById(containerId);
    if (!c || total <= 1) { if (c) c.innerHTML = ''; return; }
    let h = '';
    h += `<button ${current <= 1 ? 'disabled' : ''} onclick="${callback}(${current - 1})">上一页</button>`;
    const show = [];
    for (let i = 1; i <= total; i++) {
        if (i === 1 || i === total || (i >= current - 2 && i <= current + 2)) show.push(i);
    }
    let last = 0;
    show.forEach(i => {
        if (last && i - last > 1) h += `<button disabled>...</button>`;
        h += `<button class="${i === current ? 'active' : ''}" onclick="${callback}(${i})">${i}</button>`;
        last = i;
    });
    h += `<button ${current >= total ? 'disabled' : ''} onclick="${callback}(${current + 1})">下一页</button>`;
    c.innerHTML = h;
}

function formatDate(str) {
    if (!str) return '-';
    const d = new Date(str);
    if (isNaN(d)) return str;
    const pad = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function requireAdmin() {
    const u = getUser();
    if (!u || (u.role !== 'ADMIN' && u.role !== 'STAFF')) {
        showToast('请先以管理员或工作人员身份登录', 'error');
        setTimeout(() => location.href = 'login.html', 800);
        return false;
    }
    const nameEl = document.getElementById('adminName');
    if (nameEl) {
        const roleLabel = u.role === 'STAFF' ? '工作人员' : '管理员';
        nameEl.textContent = (u.nickname || u.username || roleLabel) + '（' + roleLabel + '）';
    }
    return true;
}

function renderSidebar() {
    const nav = document.querySelector('.sidebar-nav');
    if (nav) nav.classList.add('permission-pending');
    const page = location.pathname.split('/').pop() || 'index.html';
    document.querySelectorAll('.sidebar-nav a').forEach(a => {
        const href = a.getAttribute('href');
        if (href === page) a.classList.add('active');
        else a.classList.remove('active');
    });
    applyRoleMenuVisibility();
}

const MENU_KEY_BY_HREF = {
    'index.html': 'dashboard',
    'users.html': 'user_manage',
    'spots.html': 'spot_manage',
    'routes.html': 'route_manage',
    'culture.html': 'culture_manage',
    'hotels.html': 'hotel_manage',
    'foods.html': 'food_manage',
    'comments.html': 'comment_manage',
    'orders.html': 'order_manage',
    'faqs.html': 'faq_manage',
    'feedbacks.html': 'feedback_manage',
    'route-review.html': 'route_review',
    'spot-review.html': 'spot_review',
    'permissions.html': 'role_manage',
    'customer-service.html': 'customer_service'
};

async function applyRoleMenuVisibility() {
    const u = getUser();
    const nav = document.querySelector('.sidebar-nav');
    if (!u) {
        if (nav) nav.classList.remove('permission-pending');
        return;
    }
    if (u.role === 'ADMIN') {
        if (nav) nav.classList.remove('permission-pending');
        return;
    }
    const links = Array.from(document.querySelectorAll('.sidebar-nav a'));
    if (!links.length) {
        if (nav) nav.classList.remove('permission-pending');
        return;
    }

    let allowed = new Set();
    const roleMenus = await api('/api/admin/role/menu/list?roleCode=' + encodeURIComponent(u.role));
    if (Array.isArray(roleMenus)) {
        roleMenus
            .filter(m => m && Number(m.enabled) === 1 && m.menuKey)
            .forEach(m => allowed.add(m.menuKey));
    }
    if (!allowed.size && u.role === 'STAFF') {
        allowed = new Set(['dashboard', 'spot_manage']);
    }

    const allMenuLinks = Array.from(document.querySelectorAll('a[href]'));
    allMenuLinks.forEach(link => {
        const href = link.getAttribute('href');
        const menuKey = MENU_KEY_BY_HREF[href];
        if (!menuKey) return;
        if (link.closest('.sidebar-nav')) {
            link.style.display = allowed.has(menuKey) ? '' : 'none';
            return;
        }
        if (link.classList.contains('btn')) {
            link.style.display = allowed.has(menuKey) ? '' : 'none';
        }
    });

    const page = location.pathname.split('/').pop() || 'index.html';
    const currentMenuKey = MENU_KEY_BY_HREF[page];
    if (currentMenuKey && !allowed.has(currentMenuKey)) {
        const fallback = allowed.has('dashboard') ? 'index.html' : (allowed.has('spot_manage') ? 'spots.html' : 'login.html');
        if (page !== fallback) location.href = fallback;
    }
    if (nav) nav.classList.remove('permission-pending');
}

async function uploadFile(inputEl) {
    const file = inputEl.files[0];
    if (!file) return null;
    const fd = new FormData();
    fd.append('file', file);
    try {
        const res = await fetch(API + '/api/file/upload', { method: 'POST', body: fd, credentials: 'include' });
        const data = await res.json();
        if (data.code !== 200) { showToast(data.msg || '上传失败', 'error'); return null; }
        return data.data;
    } catch (e) {
        showToast('上传失败', 'error');
        return null;
    }
}

function doLogout() {
    clearUser();
    showToast('已退出登录');
    setTimeout(() => location.href = 'login.html', 1000);
}

/* ========== 多语言选择（注入顶栏，跨页面共享 localStorage 中的 lang） ========== */
function renderLangSwitcher() {
    const host = document.querySelector('.top-bar-right');
    if (!host || document.getElementById('adminLangSelect')) return;
    const wrap = document.createElement('label');
    wrap.className = 'admin-lang';
    wrap.innerHTML = '<span>语言</span>';
    const sel = document.createElement('select');
    sel.id = 'adminLangSelect';
    sel.innerHTML = '<option value="zh">中文</option><option value="en">English</option><option value="ja">日本語</option>';
    sel.value = adminLang;
    sel.onchange = function () {
        adminLang = this.value;
        localStorage.setItem('lang', adminLang);
        // 通知当前页面按新语言重新加载列表数据
        document.dispatchEvent(new CustomEvent('adminlangchange', { detail: adminLang }));
    };
    wrap.appendChild(sel);
    host.insertBefore(wrap, host.firstChild);
}

/* ========== 译文批量上传（CSV，整份覆盖，按 ID 对齐） ========== */
const TRANSLATION_LABELS = {
    spot: '景点', route: '线路', culture: '红色文化', food: '美食'
};

function openTranslationModal(targetType) {
    let modal = document.getElementById('translationModal');
    if (modal) modal.remove();
    const typeLabel = TRANSLATION_LABELS[targetType] || targetType;
    modal = document.createElement('div');
    modal.className = 'modal-overlay active';
    modal.id = 'translationModal';
    modal.innerHTML =
        '<div class="modal" style="width:520px">' +
            '<div class="modal-header"><h3>上传' + escHtml(typeLabel) + '译文（CSV）</h3>' +
            '<button class="modal-close" onclick="closeTranslationModal()">&times;</button></div>' +
            '<div class="modal-body">' +
                '<div class="form-group">' +
                    '<label>目标语言</label>' +
                    '<select class="form-control" id="trLang">' +
                        '<option value="en">English（英文）</option>' +
                        '<option value="ja">日本語（日文）</option>' +
                    '</select>' +
                '</div>' +
                '<div class="form-group">' +
                    '<label>译文文件（CSV，UTF-8 编码）</label>' +
                    '<input type="file" id="trFile" accept=".csv" class="form-control">' +
                    '<p class="form-tip">请先<a href="#" id="trTemplateLink">下载译文模板</a>，按模板中的 id 行填写译文列后再上传；调整文件内行顺序不会影响对应关系。</p>' +
                    '<p class="form-tip">同一语言再次上传将<b>整份覆盖</b>上一次结果；上传过程中如发生中断或错误，已保存内容会自动回滚，不会留下半份译文。缺少译文的条目将继续向游客展示中文并标注“中文”。</p>' +
                '</div>' +
                '<div id="trResult" class="tr-result" style="display:none"></div>' +
            '</div>' +
            '<div class="modal-footer">' +
                '<button class="btn btn-default" onclick="closeTranslationModal()">关闭</button>' +
                '<button class="btn btn-primary" id="trUploadBtn" onclick="submitTranslation(\'' + targetType + '\')"><i class="fas fa-upload"></i> 上传译文</button>' +
            '</div>' +
        '</div>';
    document.body.appendChild(modal);
    modal.classList.add('active');
    const link = document.getElementById('trTemplateLink');
    link.onclick = function (e) {
        e.preventDefault();
        const lang = document.getElementById('trLang').value;
        // 模板下载带登录态（管理端接口）
        fetch(API + '/api/admin/translation/template?targetType=' + targetType + '&lang=' + lang, { credentials: 'include' })
            .then(async function (res) {
                if (!res.ok) { showToast('模板下载失败', 'error'); return; }
                const blob = await res.blob();
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = targetType + '-' + lang + '-template.csv';
                document.body.appendChild(a);
                a.click();
                a.remove();
                URL.revokeObjectURL(url);
            })
            .catch(function () { showToast('模板下载失败', 'error'); });
    };
}

function closeTranslationModal() {
    const modal = document.getElementById('translationModal');
    if (modal) modal.remove();
}

async function submitTranslation(targetType) {
    const lang = document.getElementById('trLang').value;
    const input = document.getElementById('trFile');
    const file = input.files[0];
    const resultEl = document.getElementById('trResult');
    const btn = document.getElementById('trUploadBtn');
    if (!file) { showToast('请先选择 CSV 文件', 'warning'); return; }
    const fd = new FormData();
    fd.append('file', file);
    fd.append('targetType', targetType);
    fd.append('lang', lang);
    btn.disabled = true;
    resultEl.style.display = 'none';
    try {
        const res = await fetch(API + '/api/admin/translation/upload', {
            method: 'POST', body: fd, credentials: 'include'
        });
        const data = await res.json();
        if (data.code === 200) {
            showToast(data.msg || '译文上传成功');
            resultEl.style.display = 'block';
            resultEl.className = 'tr-result tr-result-ok';
            const r = data.data || {};
            resultEl.innerHTML = '<i class="fas fa-check-circle"></i> ' +
                escHtml(r.message || '上传成功') +
                '（更新 ' + (r.updatedRows || 0) + ' 条，跳过 ' + (r.skippedRows || 0) + ' 条）';
            // 通知各列表按当前语言刷新
            document.dispatchEvent(new CustomEvent('adminlangchange', { detail: adminLang }));
        } else {
            resultEl.style.display = 'block';
            resultEl.className = 'tr-result tr-result-err';
            resultEl.innerHTML = '<i class="fas fa-times-circle"></i> ' + escHtml(data.msg || '上传失败，已回滚');
        }
    } catch (e) {
        resultEl.style.display = 'block';
        resultEl.className = 'tr-result tr-result-err';
        resultEl.innerHTML = '<i class="fas fa-times-circle"></i> 上传中断：本次内容未保存，原有译文保持不变';
    } finally {
        btn.disabled = false;
    }
}

function toggleSidebar() {
    document.getElementById('sidebar').classList.toggle('open');
}

function escHtml(s) {
    if (!s) return '';
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function imgSrc(url) {
    if (!url) return '';
    if (url.startsWith('http')) return url;
    return API + url;
}

function renderStars(n) {
    n = parseInt(n) || 0;
    let h = '';
    for (let i = 1; i <= 5; i++) h += `<i class="fas fa-star ${i <= n ? '' : 'empty'}"></i>`;
    return `<span class="stars">${h}</span>`;
}

/**
 * 统一修正后台所有表格的“操作列”对齐问题：
 * 1) 自动识别表头文本为“操作”的列索引；
 * 2) 给该列 th/td 添加 action-col；
 * 3) 若 td 内存在多个按钮但未包裹 .action-btns，则自动包裹，避免换行错位。
 */
function normalizeTableActionColumns(root = document) {
    const tables = root.querySelectorAll('table');
    tables.forEach(table => {
        const headRow = table.querySelector('thead tr');
        if (!headRow) return;
        const ths = Array.from(headRow.children || []);
        const actionIdx = ths.findIndex(th => (th.textContent || '').trim() === '操作');
        if (actionIdx < 0) return;

        // header col
        if (ths[actionIdx]) ths[actionIdx].classList.add('action-col');

        // body cols
        table.querySelectorAll('tbody tr').forEach(tr => {
            const tds = tr.children;
            if (!tds || !tds[actionIdx]) return;
            const td = tds[actionIdx];
            td.classList.remove('action-btns'); // 防止 td 被当成 flex 容器导致错位
            td.classList.add('action-col');

            const hasWrapper = td.querySelector('.action-btns');
            if (!hasWrapper) {
                const controls = Array.from(td.children).filter(el =>
                    el.tagName === 'BUTTON' || el.tagName === 'A'
                );
                if (controls.length >= 1) {
                    const wrap = document.createElement('div');
                    wrap.className = 'action-btns';
                    controls.forEach(c => wrap.appendChild(c));
                    td.appendChild(wrap);
                }
            }
        });
    });
}

// 监听动态渲染（列表页大量通过 innerHTML 异步更新）
document.addEventListener('DOMContentLoaded', () => {
    renderLangSwitcher();
    normalizeTableActionColumns(document);
    const mo = new MutationObserver(() => normalizeTableActionColumns(document));
    mo.observe(document.body, { childList: true, subtree: true });
});

/* Session 超时检测 */
setInterval(async () => {
    const u = getUser();
    if (!u) return;
    try {
        const res = await fetch(API + '/api/auth/currentUser', { credentials: 'include' });
        const data = await res.json();
        if (data.code === 401) {
            clearUser();
            showToast('登录已超时，请重新登录', 'error');
            setTimeout(() => location.href = 'login.html', 1200);
        }
    } catch (e) {}
}, 300000);
