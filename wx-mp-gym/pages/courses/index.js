const app = getApp();
const request = require('../../utils/request.js');
Page({
  data: { tab: 'group', dateIdx: 0, ptDateIdx: 0, dates: [], ptDates: [], typeFilter: '', timeFilter: '', specFilter: '', courses: [], coaches: [], myBookings: [], showLoginModal: false, isLoggedIn: false },

  onLoad() {
    this.generateDates();
    this.generatePtDates();
    this.loadCourses();
    this.loadCoaches();
  },
  onShow() { this.checkLogin(); this.loadMyBookings(); },

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

  switchTab(e) { this.setData({ tab: e.currentTarget.dataset.tab }); if (e.currentTarget.dataset.tab === 'my') this.loadMyBookings(); },
  selectDate(e) { this.setData({ dateIdx: e.currentTarget.dataset.idx }); this.loadCourses(); },
  selectPtDate(e) { this.setData({ ptDateIdx: e.currentTarget.dataset.idx }); this.loadCoaches(); },
  filterType(e) { this.setData({ typeFilter: e.currentTarget.dataset.type }); this.loadCourses(); },
  filterTime(e) { this.setData({ timeFilter: e.currentTarget.dataset.time }); this.loadCourses(); },
  filterSpec(e) { this.setData({ specFilter: e.currentTarget.dataset.spec }); this.loadCoaches(); },

  loadCourses() {
    const params = {};
    if (this.data.dateIdx >= 0 && this.data.dates[this.data.dateIdx]) params.date = this.data.dates[this.data.dateIdx].full;
    if (this.data.typeFilter) params.type = this.data.typeFilter;
    if (this.data.timeFilter) params.timeSlot = this.data.timeFilter;
    request.get('/api/classes', params, (res) => { this.setData({ courses: Array.isArray(res) ? res : (res && res.list ? res.list : []) }); });
  },

  loadCoaches() {
    const params = {};
    if (this.data.specFilter) params.specialties = this.data.specFilter;
    if (this.data.ptDateIdx >= 0 && this.data.ptDates[this.data.ptDateIdx]) params.date = this.data.ptDates[this.data.ptDateIdx].full;
    request.get('/api/trainers', params, (res) => { var list = Array.isArray(res) ? res : (res && res.list ? res.list : []); list.forEach(function(c) { c.ratingStars = c.rating ? '★'.repeat(Math.floor(c.rating)) : ''; }); this.setData({ coaches: list }); });
  },

  loadMyBookings() {
    if (!app.isLoggedIn()) return;
    request.get('/api/member/bookings', { status: 'all' }, (res) => { this.setData({ myBookings: Array.isArray(res) ? res : (res && res.list ? res.list : []) }); });
  },

  goBooking(e) { if (!app.isLoggedIn()) { this.setData({ showLoginModal: true }); return; } wx.navigateTo({ url: '/pages/booking/index?type=group&id=' + e.currentTarget.dataset.id }); },
  goPTBooking(e) { if (!app.isLoggedIn()) { this.setData({ showLoginModal: true }); return; } wx.navigateTo({ url: '/pages/booking/index?type=pt&id=' + e.currentTarget.dataset.id }); },
  onLoginClose() { this.setData({ showLoginModal: false }); },
  onLoginSuccess() { this.setData({ showLoginModal: false }); }
});