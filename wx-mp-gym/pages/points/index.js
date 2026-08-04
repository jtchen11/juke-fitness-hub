var app = getApp();
var request = require('../../utils/request.js');

Page({
  data: {
    points: 0,
    rewards: [],
    history: [],
    loading: true
  },

  onShow() { this.loadData(); },

  loadData() {
    var _this = this;
    this.setData({ loading: true });
    request.get('/api/points', {}, function(r) {
      if (r && r.points !== undefined) _this.setData({ points: r.points });
    });
    request.get('/api/points/rewards', { active: true }, function(r) {
      if (r && r.list) _this.setData({ rewards: r.list });
    });
    request.get('/api/points/history', { page: 1, size: 50 }, function(r) {
      if (r && r.list) _this.setData({ history: r.list });
      _this.setData({ loading: false });
    });
  },

  goBack() { wx.navigateBack(); },

  goMyRedemptions() {
    wx.navigateTo({ url: '/pages/redemptions/index' });
  },

  onRedeem(e) {
    var id = e.currentTarget.dataset.id;
    var cost = parseInt(e.currentTarget.dataset.cost);
    var name = e.currentTarget.dataset.name;
    if (cost > this.data.points) {
      wx.showToast({ title: '积分不足', icon: 'none' });
      return;
    }
    var _this = this;
    wx.showModal({
      title: '确认兑换',
      content: '确定使用' + cost + '积分兑换“' + name + '”？',
      success: function(res) {
        if (res.confirm) {
          request.post('/api/points/redeem', { rewardId: id }, function(r) {
            if (r && r.success) {
              wx.showToast({ title: '兑换成功', icon: 'success' });
              _this.loadData();
            } else {
              wx.showToast({ title: (r && r.message) || '兑换失败', icon: 'none' });
            }
          });
        }
      }
    });
  }
});
