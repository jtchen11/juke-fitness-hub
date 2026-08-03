var app = getApp();
var request = require('../../utils/request.js');

function pad(n) { return n < 10 ? '0' + n : '' + n; }
function fmtDateTime(s) {
  if (!s) return '';
  var d = new Date(s);
  if (isNaN(d.getTime())) return s;
  return (d.getMonth() + 1) + '月' + d.getDate() + '日 ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
}
function getMemberId() {
  var u = app.globalData.userInfo || wx.getStorageSync('userInfo') || {};
  return u.id || u.memberId || 0;
}
import '../my-competitions/index.js';
Page({
  data: { list: [], loading: true },

  onShow() { this.loadData(); },

  loadData() {
    var _this = this;
    var memberId = getMemberId();
    if (!memberId) {
      this.setData({ loading: false, list: [] });
      return;
    }
    request.get('/api/competition-registrations/member/' + memberId + '/competitions', {}, function(r) {
      var now = new Date();
      var today = now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate());
      var list = (r || []).map(function(item) {
        var deadline = item.deadline ? item.deadline.slice(0, 10) : '';
        var isOpen = item.status === 'open' && (!item.deadline || deadline >= today);
        item.timeText = fmtDateTime(item.startTime) + ' - ' + fmtDateTime(item.endTime);
        item.statusText = isOpen ? '进行中' : '已结束';
        item.statusClass = isOpen ? 'st-open' : 'st-ended';
        return item;
      });
      _this.setData({ list: list, loading: false });
    }, function() {
      _this.setData({ loading: false });
    });
  },

  goDetail(e) {
    wx.navigateTo({ url: '/pages/competition-detail/index?id=' + e.currentTarget.dataset.id });
  },

  goBack() { wx.navigateBack(); }
});
