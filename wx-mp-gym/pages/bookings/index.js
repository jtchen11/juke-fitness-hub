const app = getApp();
const request = require('../../utils/request.js');
Page({
  data: { isLoggedIn: false, tab: 'group', bookings: [], showLoginModal: false },
  onShow() { this.checkLogin(); if (this.data.isLoggedIn) this.loadBookings(); },
  checkLogin() { this.setData({ isLoggedIn: app.isLoggedIn() }); },
  switchTab(e) {
    this.setData({ tab: e.currentTarget.dataset.tab });
    if (this.data.isLoggedIn) this.loadBookings();
  },
  loadBookings() {},
  showLogin() { this.setData({ showLoginModal: true }); },
  onLoginClose() { this.setData({ showLoginModal: false }); },
  onLoginSuccess() { this.checkLogin(); this.loadBookings(); }
});