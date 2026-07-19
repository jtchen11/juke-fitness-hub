var app = getApp();
var request = require('../../utils/request.js');
Page({
  data: { statusFilter: '', appointments: [] },
  onShow() { this.loadAppointments(); },
  filterStatus(e) { this.setData({ statusFilter: e.currentTarget.dataset.status }); this.loadAppointments(); },
  loadAppointments() {
    var _this = this;
    var params = {};
    if (this.data.statusFilter) params.status = this.data.statusFilter;
    var trainerId = (app.globalData.userInfo && app.globalData.userInfo.trainerId) || 0;
    request.get('/api/trainers/' + trainerId + '/appointments', params, function(r) {
      if (r) {
        var list = r.list || r || [];
        // 对缺失 memberName 的记录做兜底显示
        list.forEach(function(item) {
          if (!item.memberName || item.memberName.indexOf('#') >= 0) {
            item.memberName = item.memberNameDisplay || item.memberName || '会员';
          }
        });
        _this.setData({ appointments: list });
      }
    });
  },
  onCheckIn(e) {
    var id = e.currentTarget.dataset.id;
    var _this = this;
    wx.showModal({
      title: '教练端打卡',
      content: '请确认会员已到场，是否开始上课？',
      success: function(res) {
        if (res.confirm) {
          wx.showLoading({ title: '正在打卡...' });
          request.post('/api/check-in/pt/' + id, { action: 'start' }, function(r) {
            wx.hideLoading();
            if (r && r.success) {
              wx.showToast({ title: '打卡成功', icon: 'success' });
              _this.loadAppointments();
            } else {
              wx.showToast({ title: r.message || '打卡失败', icon: 'none' });
            }
          });
        }
      }
    });
  },
  onCancel(e) {
    var id = e.currentTarget.dataset.id;
    var _this = this;
    wx.showModal({
      title: '确认取消',
      content: '确定取消该预约吗？',
      success: function(r) {
        if (r.confirm) {
          request.post('/api/personal-trainings/' + id + '/cancel', {}, function(r) {
            if (r && r.success) { wx.showToast({ title: '已取消', icon: 'success' }); _this.loadAppointments(); }
            else { wx.showToast({ title: r.message || '操作失败', icon: 'none' }); }
          });
        }
      }
    });
  }
});
