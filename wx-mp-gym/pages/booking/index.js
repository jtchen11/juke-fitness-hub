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
    // \u67e5\u4f1a\u5458\u7b49\u7ea7\u6298\u6263
      var memberId = user.memberId;
      request.get('/api/members/' + memberId + '/benefits', null, function(ben) {
        _this.setData({ loading: false });
        var discountPct = (ben && ben.discount) || 0;
        var discounted = price * (100 - discountPct) / 100;
        var content = '\u8bfe\u7a0b\u540d\u79f0\uff1a' + t.course.name;
        content += '\n\u539f\u4ef7\uff1a\u00a5' + price.toFixed(2);
        if (discountPct > 0) {
          var levelName = ben.levelName || '';
          content += '\n' + levelName + '\u6298\u6263\uff1a-\u00a5' + (price - discounted).toFixed(2) + '\uff08' + discountPct + '%\uff09';
        }
        content += '\n\u5b9e\u4ed8\u91d1\u989d\uff1a\u00a5' + discounted.toFixed(2);
        wx.showModal({
          title: '\u786e\u8ba4\u652f\u4ed8',
          content: content,
          cancelText: '\u53d6\u6d88',
          confirmText: '\u786e\u8ba4\u652f\u4ed8',
          success: function(res) {
            if (res.confirm) {
              _this.doBook();
            }
          }
        });
      });
    } else {
      this.doBook();
    }
  },

  doBook: function() {
    var _this = this;
    var t = _this.data;
    _this.setData({ loading: true });
    request.post('/api/class-bookings', {
      classId: t.course.id,
      memberId: (app.globalData.userInfo || {}).memberId
    }, function(r) {
      _this.setData({ loading: false });
      if (r && r.success) {
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
    var _this = this;
    _this.setData({ loading: true });
    request.post('/api/class-bookings/' + _this.data.payBookingId + '/pay', null, function(r) {
      _this.setData({ loading: false, showPayModal: false });
      if (r && r.success) {
        wx.showToast({ title: '\u652f\u4ed8\u6210\u529f', icon: 'success' });
        setTimeout(function() { wx.navigateBack(); }, 1500);
      } else {
        wx.showToast({ title: (r && r.message) || '\u652f\u4ed8\u5931\u8d25', icon: 'none' });
      }
    });
  },

  closePayModal: function() {
    var _this = this;
    var bookingId = _this.data.payBookingId;
    if (bookingId) {
      request.post('/api/class-bookings/' + bookingId + '/cancel', null, function(r) {});
    }
    this.setData({ showPayModal: false, payBookingId: null, payAmount: 0, payClassName: '' });
  },

  onLoginClose: function() {
    wx.navigateBack();
  },

  onLogin: function() {
    wx.redirectTo({ url: '/pages/login/login' });
  }
});