const app = getApp();
const request = require('../../utils/request.js');
Page({
  data: { tab: 'group', dateIdx: 0, ptDateIdx: 0, dates: [], ptDates: [], typeFilter: '', specFilter: '', courses: [], coaches: [], showLoginModal: false, isLoggedIn: false },

  onLoad() {
    this.generateDates();
    this.generatePtDates();
    this.loadCourses();
    this.loadCoaches();
  },
  onShow() { this.checkLogin(); },

  checkLogin() { this.setData({ isLoggedIn: app.isLoggedIn() }); },

  generateDates() {
    const weekMap = ['日','一','二','三','四','五','六'];
    const dates = [];
    for (let i = 0; i < 7; i++) {
      const d = new Date(); d.setDate(d.getDate() + i);
      const month = d.getMonth() + 1;
      const day = d.getDate().toString().padStart(2, '0');
      dates.push({ week: i === 0 ? '今天' : i === 1 ? '明天' : '周' + weekMap[d.getDay()], day: month + '/' + day, full: d.getFullYear() + '-' + (month+'').padStart(2,'0') + '-' + day });
    }
    this.setData({ dates });
  },

  generatePtDates() {
    const weekMap = ['日','一','二','三','四','五','六'];
    const dates = [];
    for (let i = 0; i < 7; i++) {
      const d = new Date(); d.setDate(d.getDate() + i);
      const month = d.getMonth() + 1;
      const day = d.getDate().toString().padStart(2, '0');
      dates.push({ week: i === 0 ? '今天' : i === 1 ? '明天' : '周' + weekMap[d.getDay()], day: month + '/' + day, full: d.getFullYear() + '-' + (month+'').padStart(2,'0') + '-' + day });
    }
    this.setData({ ptDates: dates });
  },

  selectDate(e) { this.setData({ dateIdx: e.currentTarget.dataset.idx }); this.loadCourses(); },
  selectPtDate(e) { this.setData({ ptDateIdx: e.currentTarget.dataset.idx }); this.loadCoaches(); },
  filterType(e) { this.setData({ typeFilter: e.currentTarget.dataset.type }); this.loadCourses(); },
  filterSpec(e) { this.setData({ specFilter: e.currentTarget.dataset.spec }); this.loadCoaches(); },

  formatTimeSlot: function(start, end) {
    if (!start) return '';
    var sd = new Date(String(start).replace(/-/g, '/'));
    if (isNaN(sd.getTime())) return String(start);
    var month = String(sd.getMonth() + 1).padStart(2, '0');
    var day = String(sd.getDate()).padStart(2, '0');
    var sh = String(sd.getHours()).padStart(2, '0');
    var sm = String(sd.getMinutes()).padStart(2, '0');
    var result = month + '/' + day + ' ' + sh + ':' + sm;
    if (end) {
      var ed = new Date(String(end).replace(/-/g, '/'));
      if (!isNaN(ed.getTime())) {
        result += '-' + String(ed.getHours()).padStart(2, '0') + ':' + String(ed.getMinutes()).padStart(2, '0');
      }
    }
    return result;
  },

  resolveImageUrl(url, localPath) {
    if (!url) return localPath;
    if (url.startsWith('http')) return url;
    var name = url.split('/').pop();
    return '/images/' + localPath + '/' + name;
  },

  loadCourses() {
    var _this = this;
    var params = {};
    if (_this.data.dateIdx >= 0 && _this.data.dates[_this.data.dateIdx]) params.date = _this.data.dates[_this.data.dateIdx].full;
    if (_this.data.typeFilter) params.type = _this.data.typeFilter;
    request.get('/api/classes', params, function(res) {
      var list = Array.isArray(res) ? res : (res && res.list ? res.list : []);
      list.forEach(function(c) {
        c.coverImage = _this.resolveImageUrl(c.coverImage, 'group_class');
        c.timeDisplay = _this.formatTimeSlot(c.startTime, c.endTime);
      });
      _this.setData({ courses: list });
    });
  },

  loadCoaches() {
    const params = {};
    if (this.data.specFilter) params.specialties = this.data.specFilter;
    if (this.data.ptDateIdx >= 0 && this.data.ptDates[this.data.ptDateIdx]) params.date = this.data.ptDates[this.data.ptDateIdx].full;
    request.get('/api/trainers', params, (res) => {
      var list = Array.isArray(res) ? res : (res && res.list ? res.list : []);
      list.forEach((c) => { c.ratingStars = c.rating ? '\u2605'.repeat(Math.floor(c.rating)) : ''; c.avatar = this.resolveImageUrl(c.avatar, 'trainer'); });
      this.setData({ coaches: list });
    });
  },

  goBooking(e) { if (!app.isLoggedIn()) { this.setData({ showLoginModal: true }); return; } wx.navigateTo({ url: '/pages/booking/index?id=' + e.currentTarget.dataset.id }); },
  goPTBooking(e) {
    if (!app.isLoggedIn()) { this.setData({ showLoginModal: true }); return; }
    var id = e.currentTarget.dataset.id;
    var ptDates = this.data.ptDates;
    var ptDateIdx = this.data.ptDateIdx;
    var date = (ptDates && ptDates[ptDateIdx]) ? ptDates[ptDateIdx].full : '';
    if (!date) {
      var d = new Date();
      date = d.getFullYear() + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0');
    }
    wx.navigateTo({ url: '/pages/coach-detail/index?id=' + id + '&date=' + date });
  },
  onTabChange(e) { var tab = e.currentTarget.dataset.tab; this.setData({ tab: tab }); },
  onLoginClose() { this.setData({ showLoginModal: false }); },
  onLoginSuccess() { this.setData({ showLoginModal: false }); },
  showCodeInput(e) {
    var id = e.currentTarget.dataset.id;
    this.setData({ codeInputVisible: true, codeClassId: id, checkinCode: '' });
  },
  onCodeInput(e) { this.setData({ checkinCode: e.detail.value }); },
  submitCheckinCode() {
    var _this = this;
    var memberId = (app.globalData.userInfo || {}).memberId;
    if (!memberId) { wx.showToast({ title: '请先登录', icon: 'none' }); return; }
    if (!_this.data.checkinCode || _this.data.checkinCode.length !== 6) { wx.showToast({ title: '请输入6位签到码', icon: 'none' }); return; }
    request.post('/api/check-in/class/' + _this.data.codeClassId + '/verify-code?memberId=' + memberId + '&code=' + _this.data.checkinCode, {}, function(r) {
      if (r && r.success) {
        wx.showToast({ title: '签到成功', icon: 'success' });
        _this.setData({ codeInputVisible: false });
        _this.loadCourses();
      } else {
        wx.showToast({ title: (r && r.message) || '签到失败', icon: 'none' });
      }
    });
  },
  hideCodeInput() { this.setData({ codeInputVisible: false }); }
});
