var app = getApp();
var request = require('../../utils/request.js');
Page({
  data: { filter: 'all', list: [], showCodeInput: false, codeClassId: null, checkinCode: '', codeLoading: false },
  onShow() { this.loadList(); },
  onFilter(e) {
    var f = e.currentTarget.dataset.filter;
    this.setData({ filter: f });
    this.loadList();
  },
  loadList() {
    var that = this;
    if (!app.isLoggedIn()) { that.setData({ list: [] }); return; }
    request.get('/api/member/bookings', { status: that.data.filter }, function(res) {
      var list = res.list || res || [];
      that.setData({ list: list });
    });
  },
  statusText: function(s) {
    var map = { booked: '已预约', checked_in: '已签到', completed: '已完成', cancelled: '已取消', scheduled: '待上课' };
    return map[s] || s;
  },
  goDetail: function(e) {
    var item = e.currentTarget.dataset;
    if (item.type === 'pt') {
      wx.navigateTo({ url: '/pages/coach-appointments/detail/index?id=' + item.id });
    } else {
      wx.navigateTo({ url: '/pages/booking/index?id=' + item.id });
    }
  },
  onCancel: function(e) {
    var item = e.currentTarget.dataset;
    var that = this;
    wx.showModal({ title: '确认取消', content: '确定取消该预约吗？', success: function(r) {
      if (!r.confirm) return;
      request.post('/api/class-bookings/' + item.id + '/cancel', {}, function(res) {
        if (res.success) { wx.showToast({ title: '已取消', icon: 'success' }); that.loadList(); }
        else { wx.showToast({ title: res.message || '取消失败', icon: 'none' }); }
      });
    }});
    return false;
  },
  onCheckin: function(e) {
    var item = e.currentTarget.dataset;
    if (item.type === 'pt') {
      wx.showToast({ title: '私教请到前台刷脸签到', icon: 'none' });
      return;
    }
    this.setData({ showCodeInput: true, codeClassId: item.id, checkinCode: '' });
  },
  onCodeInput: function(e) {
    this.setData({ checkinCode: e.detail.value });
  },
  submitCheckinCode: function() {
    var that = this;
    var memberId = (app.globalData.userInfo || {}).memberId;
    if (!memberId) { wx.showToast({ title: '请先登录', icon: 'none' }); return; }
    if (!that.data.checkinCode || that.data.checkinCode.length !== 6) {
      wx.showToast({ title: '请输入6位签到码', icon: 'none' }); return;
    }
    that.setData({ codeLoading: true });
    request.post('/api/check-in/class/' + that.data.codeClassId + '/verify-code?memberId=' + memberId + '&code=' + that.data.checkinCode, {}, function(r) {
      that.setData({ codeLoading: false });
      if (r && r.success) {
        wx.showToast({ title: '签到成功', icon: 'success' });
        that.setData({ showCodeInput: false });
        that.loadList();
      } else {
        wx.showToast({ title: (r && r.message) || '签到失败', icon: 'none' });
      }
    });
  },
  hideCodeInput: function() {
    this.setData({ showCodeInput: false });
  },
  stopPropagation: function() {}
});
