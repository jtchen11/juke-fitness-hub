const app = getApp();
Page({
  data: { isLoggedIn: false, memberName: '', level: '', avatarUrl: '', stats: { bookingCount: 0, ptRemaining: 0, checkInMonth: 0, points: 0 }, showLoginModal: false },
  onShow() {
    this.checkLogin();
    if (this.data.isLoggedIn) this.loadStats();
  },
  checkLogin() {
    const u = app.globalData.userInfo || {};
    const loggedIn = app.isLoggedIn();
    this.setData({ isLoggedIn: loggedIn, memberName: u.memberName || '', level: u.level || '', avatarUrl: u.avatarUrl || '' });
  },
  loadStats() {
    var _this = this;
    const request = require('../../utils/request.js');
    request.get('/api/points', {}, function(res) {
      if (res && res.points !== undefined) {
        _this.setData({ 'stats.points': res.points });
      }
    });
  },
  onAvatarTap() { if (!this.data.isLoggedIn) this.setData({ showLoginModal: true }); },
  onRequireLogin() {
    if (!this.data.isLoggedIn) { this.setData({ showLoginModal: true }); return; }
    wx.navigateTo({ url: '/pages/my-bookings/index' });
  },
  onLoginClose() { this.setData({ showLoginModal: false }); },
  onLoginSuccess() { this.checkLogin(); this.loadStats(); },
  goCourses() { wx.switchTab({ url: '/pages/courses/index' }); },
  goPT() { wx.navigateTo({ url: '/pages/courses/index?tab=pt' }); },
  goPoints() {
    if (!app.isLoggedIn()) { this.setData({ showLoginModal: true }); return; }
    wx.navigateTo({ url: '/pages/points/index' });
  },
  goMyBookings() {
    if (!app.isLoggedIn()) { this.setData({ showLoginModal: true }); return; }
    wx.navigateTo({ url: '/pages/my-bookings/index' });
  }
});