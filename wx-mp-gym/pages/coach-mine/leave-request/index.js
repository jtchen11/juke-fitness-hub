var app = getApp();
var request = require('../../../utils/request.js');
Page({
  data: { leaveDate: '', selectedPeriod: '全天', reason: '', periods: ['全天', '上午', '下午'], submitting: false, periodMap: { '全天': 'full_day', '上午': 'morning', '下午': 'afternoon' } },
  onLoad() {
    var today = new Date();
    this.setData({ leaveDate: today.getFullYear() + '-' + String(today.getMonth()+1).padStart(2,'0') + '-' + String(today.getDate()).padStart(2,'0') });
  },
  onDateChange(e) { this.setData({ leaveDate: e.detail.value }); },
  onPeriodSelect(e) { this.setData({ selectedPeriod: e.currentTarget.dataset.period }); },
  onReasonInput(e) { this.setData({ reason: e.detail.value }); },
  goBack() { wx.navigateBack(); },
  onSubmit() {
    if (!this.data.leaveDate) { wx.showToast({ title: '请选择日期', icon: 'none' }); return; }
    var _this = this;
    this.setData({ submitting: true });
    var trainerId = app.globalData.userInfo?.trainerId || app.globalData.userInfo?.id;
    var period = _this.data.periodMap[_this.data.selectedPeriod] || 'full_day';
    request.post('/api/trainers/' + trainerId + '/leave', { leaveDate: _this.data.leaveDate, period: period, reason: _this.data.reason }, function(r) {
      _this.setData({ submitting: false });
      if (r.success) { wx.showToast({ title: '申请已提交，等待审批', icon: 'success' }); setTimeout(function() { wx.navigateBack(); }, 1500); }
      else { wx.showToast({ title: r.message || '提交失败', icon: 'none' }); }
    });
  }
});