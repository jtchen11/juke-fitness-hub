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
  goBack() { wx.navigateBack(); },
  onCheckIn() {
    var _this = this;
    wx.showModal({
      title: '确认打卡',
      content: '确认开始上课？',
      success: function(res) {
        if (res.confirm) {
          request.post('/api/check-in/pt/' + _this.data.appt.id, { action: 'start' }, function(r) {
            if (r && r.success) { wx.showToast({ title: '打卡成功', icon: 'success' }); wx.navigateBack(); }
            else { wx.showToast({ title: r.message || '打卡失败', icon: 'none' }); }
          });
        }
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
