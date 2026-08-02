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
    showLoginModal: false,
    showPayModal: false,
    showSuccessModal: false,
    successMsg: '',
    successTitle: '预约成功',
    payFreeText: '',
    payActivePkgs: [],
    payPendingPkgs: [],
    payPendingExpanded: false,
    paySingleText: '',
    payBookingCtx: null
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

    var ui = app.globalData.userInfo || wx.getStorageSync("userInfo") || {};
    var memberId = ui.memberId || ui.id;
    console.log('[coach-detail] confirmBooking memberId=', memberId, 'userInfo=', ui);
    if (!memberId) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
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
    console.log('[coach-detail] showPaymentPicker memberId=', memberId, 'packages raw=', packages, 'benefits=', benefits);
    var freeText = '';
    var activePkgs = [];
    var pendingPkgs = [];

    // 选项1：免费次数
    var freeRemaining = (benefits && benefits.freePtRemaining) || 0;
    if (freeRemaining > 0) {
      freeText = '免费次数（剩余 ' + freeRemaining + ' 次）';
    }

    // 选项2：课程包（已激活且有效全部列出；未激活未过期的收拢为“待激活”，点击展开后自动激活并使用）
    if (packages && packages.length > 0) {
      for (var i = 0; i < packages.length; i++) {
        var p = packages[i];
        var remaining = p.remainingSessions || 0;
        if (remaining <= 0) continue;
        if (p.startDate) {
          activePkgs.push({ id: p.id, name: p.packageName || '私教包', remaining: remaining, endDate: p.endDate || '长期' });
        } else {
          pendingPkgs.push({ id: p.id, name: p.packageName || '私教包', remaining: remaining });
        }
      }
    }

    // 选项3：单次付费（始终显示在最后）
    var pricePerHour = coach.pricePerHour || 300;
    var discountPct = (benefits && benefits.discount) || 0;
    var discounted = pricePerHour * (100 - discountPct) / 100;
    var priceText = '单次付费 ¥' + pricePerHour.toFixed(2);
    if (discountPct > 0) {
      priceText = '单次付费 ¥' + discounted.toFixed(2) + '（已享' + (benefits.levelName || '') + discountPct + '%折扣，原价¥' + pricePerHour.toFixed(2) + '）';
    }

    console.log('[coach-detail] activePkgs=', activePkgs.length, 'pendingPkgs=', pendingPkgs.length, 'freeText=', freeText);
    if (!freeText && activePkgs.length === 0 && pendingPkgs.length === 0) {
      wx.showToast({ title: '没有可用的支付方式', icon: 'none' });
      return;
    }

    _this.setData({
      showPayModal: true,
      payFreeText: freeText,
      payActivePkgs: activePkgs,
      payPendingPkgs: pendingPkgs,
      payPendingExpanded: false,
      paySingleText: priceText,
      payBookingCtx: { memberId: memberId, coachId: coach.id, appointmentTime: appointmentTime }
    });
  },

  togglePendingPay: function() {
    this.setData({ payPendingExpanded: !this.data.payPendingExpanded });
  },

  closePayModal: function() {
    this.setData({ showPayModal: false });
  },

  stopPropagation: function() {},

  closeSuccessModal: function() {
    this.setData({ showSuccessModal: false });
    wx.navigateBack();
  },

  selectPayMethod: function(e) {
    var _this = this;
    var type = e.currentTarget.dataset.type;
    var id = e.currentTarget.dataset.id;
    var ctx = _this.data.payBookingCtx;
    if (!ctx) return;
    var useFree = false;
    var packageId = null;
    if (type === 'free') { useFree = true; }
    else if (type === 'pkg') { packageId = Number(id); }
    _this.setData({ showPayModal: false });
    _this.doPTBook(ctx.memberId, ctx.coachId, ctx.appointmentTime, useFree, packageId);
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
        _this.setData({ showSuccessModal: true, successMsg: msg });
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