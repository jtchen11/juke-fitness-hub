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
      wx.showToast({ title: '请选择预约时段', icon: 'none' });
      return;
    }
    _this.setData({ loading: true });
    request.post('/api/personal-trainings', {
      memberId: (app.globalData.userInfo || {}).memberId,
      trainerId: t.coach.id,
      appointmentTime: t.ptDate + ' ' + t.slots[t.slotIdx] + ':00',
      durationMinutes: 60,
      useFree: false,
      packageId: null
    }, function(r) {
      _this.setData({ loading: false });
      if (r && r.success) {
        wx.showToast({ title: '预约成功', icon: 'success' });
        setTimeout(function() { wx.navigateBack(); }, 1500);
      } else {
        wx.showToast({ title: (r && r.message) || '预约失败', icon: 'none' });
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
