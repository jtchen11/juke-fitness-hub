var app = getApp();
var request = require('../../utils/request.js');

var TYPE_MAP = { pt_session: '私教课', coupon: '优惠券', physical: '实物商品', course: '课程' };
var STATUS_MAP = { pending: '待审批', approved: '已通过', completed: '已完成', rejected: '已驳回' };

function fmtTime(t) {
  if (!t) return '';
  return String(t).replace('T', ' ').substring(0, 16);
}

Page({
  data: { list: [], loading: true },

  onShow() { this.loadData(); },

  loadData() {
    var _this = this;
    this.setData({ loading: true });
    request.get('/api/points/redemptions', {}, function(r) {
      var src = (r && r.list) || [];
      var list = [];
      for (var i = 0; i < src.length; i++) {
        var item = src[i];
        list.push({
          id: item.id,
          rewardName: item.rewardName || '积分商品',
          typeText: TYPE_MAP[item.redemptionType] || item.redemptionType || '积分商品',
          statusText: STATUS_MAP[item.status] || item.status || '-',
          statusCls: 'st-' + (item.status || ''),
          pointsSpent: item.pointsSpent,
          timeText: fmtTime(item.createdAt),
          adminRemark: item.adminRemark || ''
        });
      }
      _this.setData({ list: list, loading: false });
    });
  },

  goBack() { wx.navigateBack(); }
});
