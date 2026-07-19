var app = getApp();
var request = require('../../utils/request.js');
Page({
  data: { points: 0, history: [], redeemOptions: [
    { type: 'pt_session', name: '私教课1节', desc: '使用100积分兑换一节私教课', cost: 100 },
    { type: 'coupon', name: '优惠券', desc: '使用100积分兑换优惠券（自动发放）', cost: 100 },
    { type: 'physical_goods', name: '实物商品', desc: '使用100积分兑换商品（需管理员审批）', cost: 100 }
  ]},
  onShow() { this.loadData(); },
  loadData() {
    var _this = this;
    request.get('/api/points', {}, function(r) {
      if (r && r.points !== undefined) _this.setData({ points: r.points });
    });
    request.get('/api/points/history', { page: 1, size: 50 }, function(r) {
      if (r && r.list) _this.setData({ history: r.list });
    });
  },
  onRedeem(e) {
    var type = e.currentTarget.dataset.type;
    var _this = this;
    wx.showModal({
      title: '确认兑换',
      content: '确定使用100积分兑换此商品？',
      success: function(res) {
        if (res.confirm) {
          request.post('/api/points/redeem', { type: type, remark: '' }, function(r) {
            if (r && r.success) {
              wx.showToast({ title: '兑换成功', icon: 'success' });
              _this.loadData();
            } else {
              wx.showToast({ title: r.message || '积分不足', icon: 'none' });
            }
          });
        }
      }
    });
  }
});
