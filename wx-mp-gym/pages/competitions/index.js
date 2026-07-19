var app = getApp();
var request = require('../../utils/request.js');
Page({
  data: { activeList: [], historyList: [] },
  onShow() { this.loadData(); },
  loadData() {
    var _this = this;
    request.get('/api/competitions', { page: 1, size: 50 }, function(r) {
      if (r && r.list) {
        var now = new Date().toISOString().split('T')[0];
        _this.setData({
          activeList: r.list.filter(function(i) { return i.status === 'open' || i.status === 'active'; }),
          historyList: r.list.filter(function(i) { return i.status === 'ended' || i.status === 'cancelled'; })
        });
      }
    });
  },
  goDetail(e) {
    var id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/competition-detail/index?id=' + id });
  }
});
