const app = getApp();
Page({
  data: {
    isLoggedIn: false, memberName: '', level: '', avatarUrl: '',
    stats: { bookingCount: 0, ptRemaining: 0, competitions: 0, checkInMonth: 0, points: 0 },
    banners: [
      { image: '/images/banner/banner1.png', link: '' },
      { image: '/images/banner/banner2.png', link: '' },
      { image: '/images/banner/banner3.png', link: '' }
    ],
    todayCourses: [],
    recommendedCourses: [],
    packages: [],
    fitnessData: { weight: 0, bodyFat: 0, bmi: 0, muscle: 0 }
  },
  onShow() {
    this.checkLogin();
    if (this.data.isLoggedIn) {
      this.loadStats();
      this.loadTodayCourses();
      this.loadFitnessData();
    }
    this.loadRecommendedCourses();
    this.loadPackages();
  },
  checkLogin() {
    const u = app.globalData.userInfo || {};
    const loggedIn = app.isLoggedIn();
    this.setData({ isLoggedIn: loggedIn, memberName: u.memberName || u.nickname || '', level: u.level || '', avatarUrl: u.avatarUrl || '' });
  },
  loadStats() {
    var _this = this;
    var request = require('../../utils/request.js');
    request.get('/api/members/self/stats', {}, function(res) {
      if (res) _this.setData({ stats: { bookingCount: res.bookingCount || 0, ptRemaining: res.ptRemaining || 0, competitions: res.competitions || 0, checkInMonth: res.checkInMonth || 0, points: res.points || 0 } });
    });
  },
  loadTodayCourses() {
    var _this = this;
    var request = require('../../utils/request.js');
    request.get('/api/members/today-courses', {}, function(res) {
      var list = res.list || res || [];
      // 已完成排底部
      list.sort(function(a, b) { return a.status === 'completed' ? 1 : b.status === 'completed' ? -1 : 0; });
      _this.setData({ todayCourses: list });
    });
  },
  loadRecommendedCourses() {
    var _this = this;
    var request = require('../../utils/request.js');
    request.get('/api/classes', { sort: 'enrolled', limit: 6 }, function(res) {
      var list = res.list || res || [];
      // 按预约人数排序
      list.sort(function(a, b) { return (b.enrolled || 0) - (a.enrolled || 0); });
      _this.setData({ recommendedCourses: list });
    });
  },
  loadPackages() {
    var _this = this;
    var request = require('../../utils/request.js');
    request.get('/api/private-packages/list', { active: true }, function(res) {
      var list = res.list || res || [];
      _this.setData({ packages: list });
    });
  },
  loadFitnessData() {
    var _this = this;
    var request = require('../../utils/request.js');
    request.get('/api/members/self/fitness-latest', {}, function(res) {
      if (res) _this.setData({ fitnessData: { weight: res.weight || 0, bodyFat: res.bodyFat || 0, bmi: res.bmi || 0, muscle: res.muscle || 0 } });
    });
  },
  onAvatarTap() { if (!this.data.isLoggedIn) wx.navigateTo({ url: '/pages/login/login' }); },
  onRequireLogin() { if (!this.data.isLoggedIn) { wx.navigateTo({ url: '/pages/login/login' }); return; } wx.navigateTo({ url: '/pages/face-checkin/index' }); },
  goCourses() { wx.switchTab({ url: '/pages/courses/index' }); },
  goPT() { wx.navigateTo({ url: '/pages/courses/index?tab=pt' }); },
  goMyBookings() { if (!app.isLoggedIn()) { wx.navigateTo({ url: '/pages/login/login' }); return; } wx.navigateTo({ url: '/pages/my-bookings/index' }); },
  goPackages() { if (!app.isLoggedIn()) { wx.navigateTo({ url: '/pages/login/login' }); return; } wx.navigateTo({ url: '/pages/mine/index' }); },
  goCompetitions() { if (!app.isLoggedIn()) { wx.navigateTo({ url: '/pages/login/login' }); return; } wx.navigateTo({ url: '/pages/competitions/index' }); },
  goPointsMall() { wx.showToast({ title: '功能开发中', icon: 'none' }); },
  goFitnessTest() { wx.navigateTo({ url: '/pages/assessments/index' }); },
  goBooking(e) { if (!app.isLoggedIn()) { wx.navigateTo({ url: '/pages/login/login' }); return; } wx.navigateTo({ url: '/pages/booking/index?type=group&id=' + e.currentTarget.dataset.id }); },
  goBookingDetail(e) { wx.navigateTo({ url: '/pages/my-bookings/index' }); },
  buyPackage(e) { wx.showToast({ title: '购买功能开发中', icon: 'none' }); }
});