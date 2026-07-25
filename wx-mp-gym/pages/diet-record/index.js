var app = getApp();
var request = require('../../utils/request.js');
Page({
  data: { records: [], date: '', mealType: 'breakfast', foodName: '', calories: '', mealTypes: ['breakfast', 'lunch', 'dinner', 'snack'], mealLabels: {'breakfast': '早餐', 'lunch': '午餐', 'dinner': '晚餐', 'snack': '加餐'} },
  onShow() { this.loadRecords(); },
  loadRecords() {
    var mid = app.globalData.userInfo?.memberId || 0;
    if (!mid) return;
    var _this = this;
    request.get('/api/diet-records/' + mid, {}, function(r) {
      _this.setData({ records: r || [] });
    });
  },
  onDateChange(e) { this.setData({ date: e.detail.value }); },
  onMealSelect(e) { this.setData({ mealType: e.currentTarget.dataset.type }); },
  onFoodInput(e) { this.setData({ foodName: e.detail.value }); },
  onCaloriesInput(e) { this.setData({ calories: e.detail.value }); },
  onSubmit() {
    if (!this.data.foodName) { wx.showToast({ title: '请输入食物名称', icon: 'none' }); return; }
    var mid = app.globalData.userInfo?.memberId || 0;
    var _this = this;
    request.post('/api/diet-records/' + mid, {
      date: _this.data.date || new Date().toISOString().slice(0,10),
      mealType: _this.data.mealType,
      foodName: _this.data.foodName,
      calories: parseInt(_this.data.calories) || 0
    }, function(r) {
      if (r && r.success !== false) {
        wx.showToast({ title: '添加成功', icon: 'success' });
        _this.setData({ foodName: '', calories: '' });
        _this.loadRecords();
      } else { wx.showToast({ title: r.message || '添加失败', icon: 'none' }); }
    });
  },
  onDelete(e) {
    var id = e.currentTarget.dataset.id;
    var _this = this;
    wx.showModal({ title: '确认删除', content: '确定删除该记录吗？', success: function(r) {
      if (r.confirm) {
        request.del('/api/diet-records/' + id, {}, function(res) {
          if (res && res.success !== false) { wx.showToast({ title: '已删除', icon: 'success' }); _this.loadRecords(); }
          else { wx.showToast({ title: '删除失败', icon: 'none' }); }
        });
      }
    }});
  }
});