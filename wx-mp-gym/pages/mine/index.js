var app = getApp();
Page({
  data: { isLoggedIn: false, isActiveMember: false, isVisitor: false, isExpired: false, memberName: "", level: "", avatarUrl: "", canSwitchToCoach: false, role: "", benefits: { discount: '无', freeRemaining: 0, points: 0 } },
  onShow() { this.checkLogin(); if (this.data.isLoggedIn) this.loadBenefits(); },
  checkLogin() {
    var u = app.globalData.userInfo || {};
    var loggedIn = app.isLoggedIn();
    var role = u.role || "";
    this.setData({
      isLoggedIn: loggedIn,
      isActiveMember: u.isActiveMember || false,
      isVisitor: loggedIn && !u.isActiveMember && !u.expireDate,
      isExpired: loggedIn && !u.isActiveMember && !!u.expireDate,
      memberName: u.memberName || u.nickname || "",
      level: u.level || "",
      avatarUrl: u.avatarUrl || "",
      role: role,
      canSwitchToCoach: role === "trainer" || role === "both"
    });
  },
  loadBenefits() {
    var u = app.globalData.userInfo || {};
    if (!u.memberId) return;
    var _this = this;
    var request = require('../../utils/request.js');
    request.get('/api/members/' + u.memberId + '/benefits', null, function(r) {
      if (r) {
        _this.setData({ benefits: { discount: (r.discount || 0) + '%', freeRemaining: r.freePtRemaining || 0, points: r.points || 0 } });
      }
    });
  },
  showLogin() { wx.navigateTo({ url: "/pages/login/login" }); },
  goBookings() { wx.navigateTo({ url: "/pages/my-bookings/index" }); },
  goPackages() { wx.navigateTo({ url: "/pages/my-bookings/index?tab=packages" }); },
  goPoints() { wx.navigateTo({ url: '/pages/points/index' }); },
  goFitnessTest() { wx.navigateTo({ url: "/pages/assessments/index" }); },
  goFaceCheckin() { wx.navigateTo({ url: "/pages/face-checkin/index" }); },
  goCompetitions() { wx.navigateTo({ url: "/pages/competitions/index" }); },
  goMessage() { wx.showToast({ title: '功能开发中', icon: 'none' }); },
  goPointsMall() { wx.showToast({ title: '功能开发中', icon: 'none' }); },
  goDietRecord() { wx.showToast({ title: '功能开发中', icon: 'none' }); },
  goSportsRecord() { wx.showToast({ title: '功能开发中', icon: 'none' }); },  switchToCoach() { app.switchToCoachMode(); },
  onLogout() {
    wx.showModal({ title: "提示", content: "确定退出登录？", success: function(r) { if (r.confirm) { app.logout(); wx.reLaunch({ url: "/pages/home/index" }); } } });
  }
});
