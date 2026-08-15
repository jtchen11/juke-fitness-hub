var app = getApp();
var request = require('../../utils/request.js');

Page({
  data: {
    loading: false,
    course: {},
    coverImageUrl: '',
    formattedTime: '',
    difficultyTag: 'beginner',
    trainerAvatarUrl: '',
    trainerIntro: '',
    btnText: '\u786e\u8ba4\u9884\u7ea6',
    isExperienceFree: false,
    showLoginModal: false,
    showPayModal: false,
    payBookingId: null,
    payAmount: '0.00',
    payOriginalPrice: '0.00',
    payDiscount: '0.00',
    payDiscountPct: 0,
    payLevelName: '',
    payClassName: ''
  },

  onLoad(params) {
    var _this = this;
    if (!app.isLoggedIn()) {
      _this.setData({ showLoginModal: true });
      return;
    }
    _this.loadCourse(params.id);
  },

  goBack() {
    wx.navigateBack();
  },

  resolveImageUrl: function(url, localPath) {
    if (!url) return '/images/default_course.png';
    if (url.startsWith('http')) return url;
    var name = url.split('/').pop();
    return '/images/' + localPath + '/' + name;
  },

  loadCourse(id) {
    var _this = this;
    request.get('/api/classes/' + id, null, function(r) {
      if (!r) return;
      var formatted = _this.formatTimeSlot(r.startTime, r.endTime);
      var diffTag = _this.mapDifficulty(r.difficulty);
      var isFree = r.allowVisitor && _this.isVisitorUser() && !_this.isExperienceUsed();
      var btn = isFree ? '\u514d\u8d39\u4f53\u9a8c\u9884\u7ea6' : '\u786e\u8ba4\u9884\u7ea6';
      var coverUrl = _this.resolveImageUrl(r.coverImage, 'group_class');

      _this.setData({
        course: r,
        coverImageUrl: coverUrl,
        formattedTime: formatted,
        difficultyTag: diffTag,
        btnText: btn,
        isExperienceFree: isFree
      });

      if (r.trainerId) {
        _this.loadTrainer(r.trainerId);
      }
    });
  },

  loadTrainer(trainerId) {
    var _this = this;
    request.get('/api/trainers/' + trainerId, null, function(r) {
      if (r) {
        var avatarUrl = '/images/default_avatar.png';
        if (r.avatar) {
          avatarUrl = _this.resolveImageUrl(r.avatar, 'trainer');
        }
        _this.setData({
          trainerAvatarUrl: avatarUrl,
          trainerIntro: r.intro || ''
        });
      }
    });
  },

  mapDifficulty: function(diff) {
    var map = { '\u521d\u7ea7': 'beginner', '\u4e2d\u7ea7': 'intermediate', '\u9ad8\u7ea7': 'advanced' };
    return map[diff] || 'beginner';
  },

  formatTimeSlot: function(start, end) {
    if (!start) return '';
    var startStr = String(start).replace(/-/g, '/');
    var endStr = end ? String(end).replace(/-/g, '/') : '';
    var sd = new Date(startStr);
    var ed = endStr ? new Date(endStr) : null;
    if (isNaN(sd.getTime())) return String(start);
    var fmt = function(d) {
      return d.getFullYear() + '/' + String(d.getMonth()+1).padStart(2,'0') + '/' + String(d.getDate()).padStart(2,'0')
           + ' ' + String(d.getHours()).padStart(2,'0') + ':' + String(d.getMinutes()).padStart(2,'0');
    };
    if (ed && !isNaN(ed.getTime())) {
      return fmt(sd) + '-' + String(ed.getHours()).padStart(2,'0') + ':' + String(ed.getMinutes()).padStart(2,'0');
    }
    return fmt(sd);
  },

  isVisitorUser: function() {
    var user = app.globalData.userInfo || {};
    return user.level === '\u8bbf\u5ba2';
  },

  isExperienceUsed: function() {
    var user = app.globalData.userInfo || {};
    return user.experienceUsed === true || user.experienceUsed === 1;
  },

  confirmBooking: function() {
    var _this = this;
    var t = _this.data;
    var price = parseFloat(t.course.price || 0);
    var isPaid = t.course.type === 'paid' && price > 0;
    var user = app.globalData.userInfo || {};
    var isMember = user.level && user.level !== '\u8bbf\u5ba2';

    if (isPaid && isMember) {
      _this.setData({ loading: true });
      // \u67e5\u4f1a\u5458\u7b49\u7ea7\u6298\u6263\uff0c\u586b\u5145\u81ea\u5b9a\u4e49\u652f\u4ed8\u5f39\u7a97
      var memberId = user.memberId;
      request.get('/api/members/' + memberId + '/benefits', null, function(ben) {
        _this.setData({ loading: false });
        var discountPct = (ben && ben.discount) || 0;
        var discounted = price * (100 - discountPct) / 100;
        var levelName = (ben && ben.levelName) || '';
        var discountAmount = price - discounted;
        _this.setData({
          showPayModal: true,
          payClassName: t.course.name || '',
          payOriginalPrice: price.toFixed(2),
          payDiscountPct: discountPct,
          payDiscount: discountAmount > 0 ? discountAmount.toFixed(2) : '0.00',
          payLevelName: levelName,
          payAmount: discounted.toFixed(2)
        });
      });
    } else {
      this.doBook();
    }
  },

  doBook: function() {
    var _this = this;
    var t = _this.data;
    // 访客预约体验课前检查功能开关（VISITOR_EXPERIENCE_ENABLED）
    if (_this.isVisitorUser() && t.course && t.course.allowVisitor) {
      app.ensureSystemConfig(function() {
        if (!app.isConfigEnabled('VISITOR_EXPERIENCE_ENABLED')) {
          _this.setData({ loading: false });
          wx.showToast({ title: '体验课功能暂未开放，请联系客服', icon: 'none' });
          return;
        }
        _this.doBookRequest();
      });
      return;
    }
    _this.doBookRequest();
  },

  doBookRequest: function() {
    var _this = this;
    var t = _this.data;
    _this.setData({ loading: true });
    request.post('/api/class-bookings', {
      classId: t.course.id,
      memberId: (app.globalData.userInfo || {}).memberId
    }, function(r) {
      _this.setData({ loading: false });
      if (r && r.success) {
        _this.setData({ showPayModal: false });
        var msg = r.message || '\u9884\u7ea6\u6210\u529f';
        wx.showModal({
          title: '\u9884\u7ea6\u6210\u529f',
          content: msg,
          showCancel: false,
          confirmText: '\u77e5\u9053\u4e86',
          success: function() { wx.navigateBack(); }
        });
      } else {
        wx.showToast({ title: (r && r.message) || '\u9884\u7ea6\u5931\u8d25', icon: 'none' });
      }
    });
  },

  confirmPay: function() {
    // \u786e\u8ba4\u652f\u4ed8\u540e\u6267\u884c\u9884\u7ea6\uff08\u524d\u7aef\u63a7\u5236\uff1a\u70b9\u51fb\u786e\u8ba4\u624d\u8c03\u7528\u9884\u7ea6\u63a5\u53e3\uff09
    this.doBook();
  },

  closePayModal: function() {
    // \u53d6\u6d88\u5373\u7ec8\u6b62\u6d41\u7a0b\uff0c\u4e0d\u6267\u884c\u9884\u7ea6
    this.setData({ showPayModal: false, payBookingId: null, payAmount: '0.00', payOriginalPrice: '0.00', payDiscount: '0.00', payDiscountPct: 0, payLevelName: '', payClassName: '' });
  },

  onLoginClose: function() {
    wx.navigateBack();
  },

  onLogin: function() {
    wx.redirectTo({ url: '/pages/login/login' });
  }
});