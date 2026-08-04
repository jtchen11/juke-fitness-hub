var app = getApp(); var request = require('../../utils/request.js');  Page({   data: {     isLoggedIn: false, memberName: '', level: '', avatarUrl: '',     stats: { bookingCount: 0, ptRemaining: 0, competitions: 0, checkInMonth: 0, points: 0 },     banners: [       { image: '/images/banner/banner1.png', link: '' },       { image: '/images/banner/banner2.png', link: '' },       { image: '/images/banner/banner3.png', link: '' }     ],     todayCourses: [],     recommendedCourses: [],     packages: [],     fitnessData: { weight: 0, bodyFat: 0, bmi: 0, muscle: 0 },     showExperiencePopup: false, showPayModal: false, payPkg: null, payDiscount: 0, payLevelName: '', payOriginalPrice: 0, payFinalPrice: '0.00'   },   onShow() {     this.checkLogin();     if (this.data.isLoggedIn) {       this.loadStats();       this.loadTodayCourses();       this.loadFitnessData();     }     this.loadRecommendedCourses();     this.loadPackages();     this.checkVisitorExperience();   },   checkLogin() {     var u = app.globalData.userInfo || {};     var loggedIn = app.isLoggedIn();     this.setData({ isLoggedIn: loggedIn, memberName: u.memberName || u.nickname || '', level: u.level || '', avatarUrl: u.avatarUrl || '' });   },   checkVisitorExperience() {     var u = app.globalData.userInfo || {};     var level = (u.level || '').trim();     var used = u.experienceUsed === true;     console.log('[体验课弹窗] level=' + level + ' experienceUsed=' + (u.experienceUsed===null?'null':u.experienceUsed));     if (level === '访客' && !used) {       this.setData({ showExperiencePopup: true });       wx.setStorageSync('visitorDialogShown', true);     }   },   closeExperiencePopup() {     this.setData({ showExperiencePopup: false });   },   goExperienceBooking() {     this.setData({ showExperiencePopup: false });     wx.reLaunch({ url: '/pages/courses/index' });   },   loadStats() {     var _this = this;     request.get('/api/members/self/stats', {}, function(res) {       if (res) _this.setData({ stats: { bookingCount: res.bookingCount || 0, ptRemaining: res.ptRemaining || 0, competitions: res.competitions || 0, checkInMonth: res.checkInMonth || 0, points: res.points || 0 } });     });   },   loadTodayCourses() {     var _this = this;     request.get('/api/members/today-courses', {}, function(res) {       var list = res.list || res || [];       list.sort(function(a, b) { return a.status === 'completed' ? 1 : b.status === 'completed' ? -1 : 0; });       _this.setData({ todayCourses: list });     });   },   resolveImageUrl: function(url, localPath) {     if (!url) return '/images/default_course.png';     if (url.startsWith('http')) return url;     var name = url.split('/').pop();     return '/images/' + localPath + '/' + name;   },   loadRecommendedCourses() {     var _this = this;     request.get('/api/classes', { size: 20 }, function(res) {       var list = res.list || res || [];       var now = new Date();       list = list.filter(function(c) {         if (c.status && c.status !== 'scheduled') return false;         if (c.endTime) {           var end = new Date(String(c.endTime).replace(/-/g, '/'));           if (!isNaN(end.getTime()) && end < now) return false;         }         var enrolled = Number(c.enrolled) || 0;         var maxCap = Number(c.maxCapacity) || 0;         if (maxCap > 0 && enrolled >= maxCap) return false;         return true;       });       list.sort(function(a, b) { return (Number(b.enrolled) || 0) - (Number(a.enrolled) || 0); });       list = list.slice(0, 4);       list.forEach(function(c) {         c.cover = _this.resolveImageUrl(c.coverImage, 'group_class');       });       _this.setData({ recommendedCourses: list });     });   },   loadPackages() {
    var _this = this;
    request.get('/api/private-packages/list', { active: true }, function(res) {
      var list = res.list || res || [];
      var discounted = list.filter(function(p) {
        var price = Number(p.price) || 0;
        var original = Number(p.originalPrice) || 0;
        return p.isActive !== false && original > price && price > 0;
      });
      discounted.sort(function(a, b) {
        var da = (Number(a.originalPrice) - Number(a.price)) / Number(a.originalPrice);
        var db = (Number(b.originalPrice) - Number(b.price)) / Number(b.originalPrice);
        return db - da;
      });
      var top = discounted.slice(0, 3);
      top.forEach(function(p) {
        var price = Number(p.price) || 0;
        var original = Number(p.originalPrice) || 0;
        p.discount = original > price ? Math.round(price / original * 10) : null;
      });
      _this.setData({ packages: top });
    });
  },   loadFitnessData() {     var _this = this;     request.get('/api/members/self/fitness-latest', {}, function(res) {       if (res) _this.setData({ fitnessData: { weight: res.weight || 0, bodyFat: res.bodyFat || 0, bmi: res.bmi || 0, muscle: res.muscle || 0 } });     });   },   onAvatarTap() { if (!this.data.isLoggedIn) wx.navigateTo({ url: '/pages/login/login' }); },   onRequireLogin() { if (!this.data.isLoggedIn) { wx.navigateTo({ url: '/pages/login/login' }); return; } wx.navigateTo({ url: '/pages/face-checkin/index' }); },   goCourses() { wx.reLaunch({ url: '/pages/courses/index' }); },   goPT() { wx.navigateTo({ url: '/pages/courses/index?tab=pt' }); },   goMyBookings() { if (!app.isLoggedIn()) { wx.navigateTo({ url: '/pages/login/login' }); return; } wx.navigateTo({ url: '/pages/my-bookings/index' }); },   goPackages() { if (!app.isLoggedIn()) { wx.navigateTo({ url: '/pages/login/login' }); return; } wx.navigateTo({ url: '/pages/packages/index' }); },   goCompetitions() { if (!app.isLoggedIn()) { wx.navigateTo({ url: '/pages/login/login' }); return; } wx.navigateTo({ url: '/pages/competitions/index' }); },   goMyCompetitions() { if (!app.isLoggedIn()) { wx.navigateTo({ url: '/pages/login/login' }); return; } wx.navigateTo({ url: '/pages/my-competitions/index' }); },   goPointsMall() { if (!app.isLoggedIn()) { wx.navigateTo({ url: '/pages/login/login' }); return; } wx.navigateTo({ url: '/pages/points/index' }); },   goFitnessTest() { if (!app.isLoggedIn()) { wx.navigateTo({ url: '/pages/login/login' }); return; } wx.navigateTo({ url: '/pages/assessments/index' }); },   goBooking(e) { if (!app.isLoggedIn()) { wx.navigateTo({ url: '/pages/login/login' }); return; } wx.navigateTo({ url: '/pages/booking/index?id=' + e.currentTarget.dataset.id }); },   goBookingDetail(e) { wx.navigateTo({ url: '/pages/my-bookings/index' }); },   buyPackage(e) {
  var id = e.currentTarget.dataset.id;
  var pkg = this.data.packages.find(function(i) { return i.id === id; });
  if (!pkg) return;
  var discount = this.getDiscountPercent();
  var price = Number(pkg.price) || 0;
  var finalPrice = price * (1 - discount / 100);
  this.setData({ showPayModal: true, payPkg: pkg, payDiscount: discount, payLevelName: (app.globalData.userInfo || {}).level || '\u666e\u901a\u4f1a\u5458', payOriginalPrice: price, payFinalPrice: finalPrice.toFixed(2) });
},
getDiscountPercent: function() {
  var level = (app.globalData.userInfo || {}).level || '';
  if (level === '\u94c2\u91d1\u4f1a\u5458') return 20;
  if (level === '\u9ec4\u91d1\u4f1a\u5458') return 10;
  return 0;
},
confirmBuy() {
  var that = this;
  var pkg = that.data.payPkg;
  if (!pkg) return;
  var u = app.globalData.userInfo || {};
  if (!u.memberId) { wx.showToast({ title: '\u8bf7\u5148\u767b\u5f55', icon: 'none' }); return; }
  that.setData({ showPayModal: false });
  wx.showLoading({ title: '\u8d2d\u4e70\u4e2d...' });
  request.post('/api/private-packages/buy', { memberId: u.memberId, packageId: pkg.id }, function(r) {
    wx.hideLoading();
    if (r && r.success) {
      wx.showToast({ title: '\u8d2d\u4e70\u6210\u529f', icon: 'success' });
      that.loadPackages();
    } else {
      wx.showToast({ title: (r && r.message) || '\u8d2d\u4e70\u5931\u8d25', icon: 'none' });
    }
  });
},
closePayModal() { this.setData({ showPayModal: false }); }, });