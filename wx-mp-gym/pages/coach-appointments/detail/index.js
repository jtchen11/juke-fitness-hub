var app = getApp();
var request = require('../../../utils/request.js');
Page({
  data: { appt: {}, statusText: '' },
  onLoad(params) {
    var _this = this;
    request.get('/api/trainers/appointments/' + params.id, {}, function(r) {
      if (r) {
        var map = { scheduled: '待上课', checked_in: '已核销', completed: '已完成', cancelled: '已取消' };
        _this.setData({ appt: r, statusText: map[r.status] || r.status });
      }
    });
  },
  getMemberId() {
    return app.globalData.userInfo?.memberId || 0;
  },
  goBack() { wx.navigateBack(); },
  onCheckIn() {
    var _this = this;
    var memberId = this.getMemberId();
    if (!memberId) { wx.showToast({ title: '请先登录', icon: 'none' }); return; }
    wx.showModal({
      title: '确认上课',
      content: '确认开始上课？需完成会员人脸识别+位置确认',
      success: function(res) {
        if (res.confirm) {
          request.post('/api/check-in/pt/coach/' + _this.data.appt.id + '?memberId=' + memberId + '&action=start', {}, function(r) {
            if (r && r.success) { wx.showToast({ title: '上课打卡成功', icon: 'success' }); _this.loadDetail(); }
            else { wx.showToast({ title: r.message || '打卡失败', icon: 'none' }); }
          });
        }
      }
    });
  },
  onCheckOut() {
    var _this = this;
    var memberId = this.getMemberId();
    if (!memberId) { wx.showToast({ title: '请先登录', icon: 'none' }); return; }
    wx.showModal({
      title: '确认下课',
      content: '确认结束课程？同样需完成会员人脸识别+位置确认',
      success: function(res) {
        if (res.confirm) {
          request.post('/api/check-in/pt/coach/' + _this.data.appt.id + '?memberId=' + memberId + '&action=end', {}, function(r) {
            if (r && r.success) { wx.showToast({ title: '下课打卡成功', icon: 'success' }); _this.loadDetail(); }
            else { wx.showToast({ title: r.message || '打卡失败', icon: 'none' }); }
          });
        }
      }
    });
  },
  loadDetail() {
    var _this = this;
    request.get('/api/trainers/appointments/' + _this.data.appt.id, {}, function(r) {
      if (r) {
        var map = { scheduled: '待上课', checked_in: '已核销', completed: '已完成', cancelled: '已取消' };
        _this.setData({ appt: r, statusText: map[r.status] || r.status });
      }
    });
  },
  onCancel() {
    var _this = this;
    wx.showModal({
      title: '确认取消',
      content: '确定取消该预约？',
      success: function(r) {
        if (r.confirm) {
          request.post('/api/personal-trainings/' + _this.data.appt.id + '/cancel', {}, function(r) {
            if (r && r.success) { wx.showToast({ title: '已取消', icon: 'success' }); wx.navigateBack(); }
            else { wx.showToast({ title: r.message || '取消失败', icon: 'none' }); }
          });
        }
      }
    });
  }
});