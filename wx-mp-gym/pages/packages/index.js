var app = getApp();
var request = require('../../utils/request.js');
Page({
  data: { list: [], shopList: [], showPayModal: false, payPkg: null, payDiscount: 0, payLevelName: '', payOriginalPrice: 0, payFinalPrice: '0.00' },
  onShow() { this.loadList(); this.loadShop(); },
  loadList() {
    var that = this;
    var u = app.globalData.userInfo || {};
    if (!u.memberId) { that.setData({ list: [] }); return; }
    request.get('/api/members/' + u.memberId + '/packages', null, function(res) {
      var list = Array.isArray(res) ? res : [];
      var today = that.formatDate(new Date());
      list.forEach(function(p) {
        var remaining = Number(p.remainingSessions) || 0;
        var endDate = p.endDate ? String(p.endDate) : '';
        var startDate = p.startDate ? String(p.startDate) : '';
        var actDeadline = p.activationDeadline ? String(p.activationDeadline) : '';
        if (remaining <= 0) { p.statusText = '已用完'; p.statusClass = 'used'; }
        else if (!startDate && actDeadline && actDeadline < today) { p.statusText = '已失效'; p.statusClass = 'expired'; }
        else if (!startDate) { p.statusText = '未激活'; p.statusClass = 'pending'; }
        else if (endDate && endDate < today) { p.statusText = '已过期'; p.statusClass = 'expired'; }
        else { p.statusText = '使用中'; p.statusClass = 'active'; }
      });
      that.setData({ list: list });
    });
  },
  loadShop() {
    var that = this;
    request.get('/api/private-packages/list', null, function(res) {
      that.setData({ shopList: Array.isArray(res) ? res : [] });
    });
  },
  formatDate: function(d) {
    var mm = String(d.getMonth() + 1).padStart(2, '0');
    var dd = String(d.getDate()).padStart(2, '0');
    return d.getFullYear() + '-' + mm + '-' + dd;
  },
  getDiscountPercent: function() {
    var level = (app.globalData.userInfo || {}).level || '';
    if (level === '铂金会员') return 20;
    if (level === '黄金会员') return 10;
    return 0;
  },
  buyPackage(e) {
    var id = e.currentTarget.dataset.id;
    var pkg = this.data.shopList.find(function(i) { return i.id === id; });
    if (!pkg) return;
    var discount = this.getDiscountPercent();
    var price = Number(pkg.price) || 0;
    var finalPrice = price * (1 - discount / 100);
    this.setData({
      showPayModal: true,
      payPkg: pkg,
      payDiscount: discount,
      payLevelName: (app.globalData.userInfo || {}).level || '普通会员',
      payOriginalPrice: price,
      payFinalPrice: finalPrice.toFixed(2)
    });
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
