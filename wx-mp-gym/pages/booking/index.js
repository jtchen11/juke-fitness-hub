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
    btnText: '确认预约',
    isExperienceFree: false,
    showLoginModal: false,
    showPayModal: false,
    payBookingId: null,
    payAmount: 0,
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
      var btn = isFree ? '免费体验预约' : '确认预约';
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
    var map = { '初级': 'beginner', '中级': 'intermediate', '高级': 'advanced' };
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
    return user.level === '访客';
  },

  isExperienceUsed: function() {
    var user = app.globalData.userInfo || {};
    return user.experienceUsed === true || user.experienceUsed === 1;
  },

  confirmBooking: function() {
    var _this = this;
    var t = _this.data;
    _this.setData({ loading: true });
    request.post('/api/class-bookings', {
      classId: t.course.id,
      memberId: (app.globalData.userInfo || {}).memberId
    }, function(r) {
      _this.setData({ loading: false });
      if (r && r.success) {
        if (r.amount && parseFloat(r.amount) > 0) {
          _this.setData({
            showPayModal: true,
            payBookingId: r.bookingId,
            payAmount: r.amount,
            payClassName: r.className || t.course.name
          });
        } else {
          wx.showToast({ title: "预约成功", icon: "success" });
          _this.loadCourse(t.course.id);
          setTimeout(function() { wx.navigateBack(); }, 1500);
        }
      } else {
        wx.showToast({ title: (r && r.message) || '预约失败', icon: 'none' });
      }
    });
  },

  confirmPay: function() {
    var _this = this;
    _this.setData({ loading: true });
    request.post('/api/class-bookings/' + _this.data.payBookingId + '/pay', null, function(r) {
      _this.setData({ loading: false, showPayModal: false });
      if (r && r.success) {
        wx.showToast({ title: '支付成功', icon: 'success' });
        setTimeout(function() { wx.navigateBack(); }, 1500);
      } else {
        wx.showToast({ title: (r && r.message) || '支付失败', icon: 'none' });
      }
    });
  },

  closePayModal: function() {
    this.setData({ showPayModal: false });
  },

  onLoginClose: function() {
    wx.navigateBack();
  },

  onLogin: function() {
    wx.redirectTo({ url: '/pages/login/login' });
  }
});
