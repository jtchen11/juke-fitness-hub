var app = getApp();
Page({
  data: { isLoggedIn: false, isActiveMember: false, isVisitor: false, isExpired: false, memberName: "", level: "", avatarUrl: "", canSwitchToCoach: false, role: "" },
  onShow() { this.checkLogin(); },
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
  showLogin() { wx.navigateTo({ url: "/pages/login/login" }); },
  goBookings() { wx.navigateTo({ url: "/pages/my-bookings/index" }); },
  goPackages() {},
  goPoints() { wx.navigateTo({ url: '/pages/points/index' }); },
  goFitnessTest() { wx.navigateTo({ url: "/pages/assessments/index" }); },
  goFaceCheckin() { wx.navigateTo({ url: "/pages/face-checkin/index" }); },
  goCompetitions() { wx.navigateTo({ url: "/pages/competitions/index" }); },
  switchToCoach() { app.switchToCoachMode(); },
  onLogout() {
    wx.showModal({ title: "提示", content: "确定退出登录？", success: function(r) { if (r.confirm) { app.logout(); wx.reLaunch({ url: "/pages/home/index" }); } } });
  }
});