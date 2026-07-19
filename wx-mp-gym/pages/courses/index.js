const app = getApp();
const request = require('../../utils/request.js');
Page({
  data: { tab: 'group', dateIdx: 0, dates: [], typeFilter: '', specFilter: '', courses: [], coaches: [], myBookings: [], showLoginModal: false, isLoggedIn: false },

  onLoad() { this.generateDates(); this.loadCourses(); this.loadCoaches(); },
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

  switchTab(e) { this.setData({ tab: e.currentTarget.dataset.tab }); if (e.currentTarget.dataset.tab === 'my') { this.loadMyBookings(); } },

  selectDate(e) { this.setData({ dateIdx: e.currentTarget.dataset.idx }); this.loadCourses(); },
  filterType(e) { this.setData({ typeFilter: e.currentTarget.dataset.type }); this.loadCourses(); },
  filterSpec(e) { this.setData({ specFilter: e.currentTarget.dataset.spec }); this.loadCoaches(); },

  loadCourses() {
    const params = {};
    if (this.data.dateIdx >= 0 && this.data.dates[this.data.dateIdx]) params.date = this.data.dates[this.data.dateIdx].full;
    if (this.data.typeFilter) params.type = this.data.typeFilter;
    request.get('/api/classes', params, (res) => { this.setData({ courses: res.list || [] }); });
  },

  loadCoaches() {
    const params = {};
    if (this.data.specFilter) params.specialties = this.data.specFilter;
    request.get('/api/trainers', params, (res) => { this.setData({ coaches: res.list || [] }); });
  },

  loadMyBookings() {
    if (!app.isLoggedIn()) return;
    request.get('/api/member/bookings', { status: 'all' }, (res) => {
      this.setData({ myBookings: res.list || [] });
    });
  },

  goBooking(e) {
    if (!app.isLoggedIn()) { this.setData({ showLoginModal: true }); return; }
    wx.navigateTo({ url: '/pages/booking/index?type=group&id=' + e.currentTarget.dataset.id });
  },

  goPTBooking(e) {
    if (!app.isLoggedIn()) { this.setData({ showLoginModal: true }); return; }
    wx.navigateTo({ url: '/pages/booking/index?type=pt&id=' + e.currentTarget.dataset.id });
  },

  onLoginClose() { this.setData({ showLoginModal: false }); },
  onLoginSuccess() { this.setData({ showLoginModal: false }); }
});