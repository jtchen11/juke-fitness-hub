var app = getApp();
var request = require('../../utils/request.js');
Page({
  data: { list: [], shopList: [], showPayModal: false, payPkg: null },
  onShow() { this.loadList(); this.loadShop(); },
  loadList() {
    var that = this;
    var u = app.globalData.userInfo || {};
    if (!u.memberId) { that.setData({ list: [] }); return; }
    request.get('/api/members/' + u.memberId + '/packages', null, function(res) {
      that.setData({ list: Array.isArray(res) ? res : [] });
    });
  },
  loadShop() {
    var that = this;
    request.get('/api/private-packages/list', null, function(res) {
      that.setData({ shopList: Array.isArray(res) ? res : [] });
    });
  },
  buyPackage(e) {
    var id = e.currentTarget.dataset.id;
    var pkg = this.data.shopList.find(function(i) { return i.id === id; });
    if (!pkg) return;
    this.setData({ showPayModal: true, payPkg: pkg });
  },
  confirmBuy() {
    var that = this;
    var pkg = that.data.payPkg;
    if (!pkg) return;
    var u = app.globalData.userInfo || {};
    if (!u.memberId) { wx.showToast({ title: '请先登录', icon: 'none' }); return; }
    that.setData({ showPayModal: false });
    wx.showLoading({ title: '购买中...' });
    request.post('/api/private-packages/buy', { memberId: u.memberId, packageId: pkg.id }, function(r) {
      wx.hideLoading();
      if (r && r.success) {
        wx.showToast({ title: '购买成功', icon: 'success' });
        that.loadList();
      } else {
        wx.showToast({ title: (r && r.message) || '购买失败', icon: 'none' });
      }
    });
  },
  closePayModal() { this.setData({ showPayModal: false }); },
  goBack() { wx.navigateBack(); }
});
