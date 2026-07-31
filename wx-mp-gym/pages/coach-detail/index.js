var app = getApp();
var request = require('../../utils/request.js');

Page({
  data: {
    loading: false,
    coach: {},
    coachAvatarUrl: '',
    totalClasses: 0,
    ptDate: '',
    ptDateDisplay: '',
    slots: [],
    slotIdx: -1,
    showLoginModal: false
  },

  onLoad(params) {
    var _this = this;
    if (!app.isLoggedIn()) {
      _this.setData({ showLoginModal: true });
      return;
    }
    var date = params.date || '';
    var display = date ? date.replace(/-/g, '/') : '';
    _this.setData({ ptDate: date, ptDateDisplay: display });
    _this.loadCoach(params.id);
  },

  goBack() {
    wx.navigateBack();
  },

  resolveImageUrl: function(url, localPath) {
    if (!url) return '/images/default_avatar.png';
    if (url.startsWith('http')) return url;
    var name = url.split('/').pop();
    return '/images/' + localPath + '/' + name;
  },

  loadCoach(id) {
    var _this = this;
    request.get('/api/trainers/' + id, null, function(r) {
      if (!r) return;
      var avatarUrl = _this.resolveImageUrl(r.avatar, 'trainer');
      _this.setData({
        coach: r,
        coachAvatarUrl: avatarUrl
      });
      if (_this.data.ptDate) {
        _this.loadSlots();
      }
    });
    request.get('/api/trainers/' + id + '/stats', null, function(r) {
      if (r && r.totalClasses !== undefined) {
        _this.setData({ totalClasses: r.totalClasses });
      }
    });
  },

  loadSlots() {
    var _this = this;
    if (!_this.data.ptDate || !_this.data.coach.id) return;
    request.get('/api/trainers/' + _this.data.coach.id + '/slots', { date: _this.data.ptDate }, function(r) {
      _this.setData({ slots: Array.isArray(r) ? r : [] });
    });
  },

  selectSlot(e) {
    this.setData({ slotIdx: e.currentTarget.dataset.idx });
  },

  confirmBooking() {
    var _this = this;
    var t = _this.data;
    if (!t.ptDate || t.slotIdx < 0) {
      wx.showToast({ title: '\u8bf7\u9009\u62e9\u9884\u7ea6\u65f6\u6bb5', icon: 'none' });
      return;
    }
    _this.setData({ loading: true });

    var memberId = (app.globalData.userInfo || {}).memberId;
    var coach = t.coach;
    var appointmentTime = t.ptDate + ' ' + t.slots[t.slotIdx] + ':00';


    // \u5e76\u884c\u67e5\u8be2\uff1a\u4f1a\u5458\u6743\u76ca + \u8bfe\u7a0b\u5305\u5217\u8868
    var done = { benefits: false, packages: false, benData: null, pkgData: null };
    function tryShowPicker() {
      if (!done.benefits || !done.packages) return;
      _this.setData({ loading: false });
      _this.showPaymentPicker(memberId, coach, appointmentTime, done.benData, done.pkgData);
    }
    request.get('/api/members/' + memberId + '/benefits', null, function(r) {
      done.benefits = true;
      done.benData = r || {};
      tryShowPicker();
    });
    request.get('/api/private-packages/mine', { memberId: memberId }, function(r) {
      done.packages = true;
      done.pkgData = Array.isArray(r) ? r : [];
      tryShowPicker();
    });
  },

  showPaymentPicker: function(memberId, coach, appointmentTime, benefits, packages) {
    var _this = this;
    var itemList = [];
    var itemData = [];

    // 选项1：免费次数
    var freeRemaining = (benefits && benefits.freePtRemaining) || 0;
    if (freeRemaining > 0) {
      itemList.push('\u514d\u8d39\u6b21\u6570\uff08\u5269\u4f59 ' + freeRemaining + ' \u6b21\uff09');
      itemData.push({ useFree: true, packageId: null, label: 'free' });
    }

    // 选项2：课程包
    if (packages && packages.length > 0) {
      for (var i = 0; i < packages.length; i++) {
        var p = packages[i];
        var remaining = p.remainingSessions || 0;
        if (remaining <= 0) continue;
        itemList.push('\u8bfe\u7a0b\u5305\uff08' + (p.packageName || '') + '\uff0c\u5269\u4f59 ' + remaining + ' \u8282\uff09');
        itemData.push({ useFree: false, packageId: p.id, label: 'package_' + i });
      }
    }

    // 选项3：单次付费
    var pricePerHour = coach.pricePerHour || 300;
    var discountPct = (benefits && benefits.discount) || 0;
    var discounted = pricePerHour * (100 - discountPct) / 100;
    var priceText = '\u5355\u6b21\u4ed8\u8d39 \u00a5' + pricePerHour.toFixed(2);
    if (discountPct > 0) {
      priceText = '\u5355\u6b21\u4ed8\u8d39 \u00a5' + discounted.toFixed(2) + '\uff08\u5df2\u4eab' + (benefits.levelName || '') + discountPct + '%\u6298\u6263\uff0c\u539f\u4ef7\u00a5' + pricePerHour.toFixed(2) + '\uff09';
    }
    itemList.push(priceText);
    itemData.push({ useFree: false, packageId: null, label: 'single' });

    if (itemList.length === 0) {
      wx.showToast({ title: '\u6ca1\u6709\u53ef\u7528\u7684\u652f\u4ed8\u65b9\u5f0f', icon: 'none' });
      return;
    }

    wx.showActionSheet({
      itemList: itemList,
      success: function(res) {
        var selected = itemData[res.tapIndex];
        _this.doPTBook(memberId, coach.id, appointmentTime, selected.useFree, selected.packageId);
      }
    });
  },

  doPTBook: function(memberId, trainerId, appointmentTime, useFree, packageId) {
    var _this = this;
    _this.setData({ loading: true });
    request.post('/api/personal-trainings', {
      memberId: memberId,
      trainerId: trainerId,
      appointmentTime: appointmentTime,
      durationMinutes: 60,
      useFree: useFree,
      packageId: packageId
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

  onLoginClose() {
    wx.navigateBack();
  },

  onLogin() {
    wx.redirectTo({ url: '/pages/login/login' });
  }
});