const app = getApp();
const request = require('../../utils/request.js');
Page({
  data: { type: '', loading: false, course: {}, coach: {}, ptDate: '', minDate: '', slots: [], slotIdx: -1, payMethod: '', pkgRemaining: 0, freeRemaining: 0, showLoginModal: false },
  onLoad(params) {
    if (!app.isLoggedIn()) { this.setData({ showLoginModal: true }); return; }
    const today = new Date();
    this.setData({ type: params.type, minDate: today.getFullYear() + '-' + String(today.getMonth()+1).padStart(2,'0') + '-' + String(today.getDate()).padStart(2,'0') });
    if (params.type === 'group') this.loadCourse(params.id);
    else this.loadCoach(params.id);
  },
  goBack() { wx.navigateBack(); },
  loadCourse(id) { request.get('/api/classes/' + id, null, (r) => { this.setData({ course: r }); }); },
  loadCoach(id) {
    request.get('/api/trainers/' + id, null, (r) => { this.setData({ coach: r }); });
    this.loadUserBenefits();
  },
  loadUserBenefits() {
    const u = app.globalData.userInfo || {};
    if (u.memberId) { request.get('/api/members/' + u.memberId + '/benefits', null, (r) => { if (r.freePtRemaining) this.setData({ freeRemaining: r.freePtRemaining }); }); }
  },
  onDateChange(e) { this.setData({ ptDate: e.detail.value, slotIdx: -1, slots: [] }); this.loadSlots(); },
  loadSlots() {
    if (!this.data.ptDate || !this.data.coach.id) return;
    request.get('/api/trainers/' + this.data.coach.id + '/slots', { date: this.data.ptDate }, (r) => { this.setData({ slots: r || [] }); });
  },
  selectSlot(e) { this.setData({ slotIdx: e.currentTarget.dataset.idx }); },
  selectPay(e) { this.setData({ payMethod: e.currentTarget.dataset.method }); },
  confirmBooking() {
    const t = this.data;
    if (t.type === 'group') {
      this.setData({ loading: true });
      request.post('/api/class-bookings', { classId: t.course.id }, (r) => {
        this.setData({ loading: false });
        if (r.success) { wx.showToast({ title: '预约成功', icon: 'success' }); setTimeout(() => wx.navigateBack(), 1500); }
        else { wx.showToast({ title: r.message || '预约失败', icon: 'none' }); }
      });
    } else {
      if (!t.ptDate || t.slotIdx < 0) { wx.showToast({ title: '请选择日期和时段', icon: 'none' }); return; }
      if (!t.payMethod) { wx.showToast({ title: '请选择支付方式', icon: 'none' }); return; }
      this.setData({ loading: true });
      request.post('/api/personal-trainings', {
        memberId: app.globalData.userInfo.memberId, trainerId: t.coach.id,
        appointmentTime: t.ptDate + ' ' + t.slots[t.slotIdx] + ':00',
        durationMinutes: 60, useFree: t.payMethod === 'free', packageId: null
      }, (r) => {
        this.setData({ loading: false });
        if (r.success) { wx.showToast({ title: '预约成功', icon: 'success' }); setTimeout(() => wx.navigateBack(), 1500); }
        else { wx.showToast({ title: r.message || '预约失败', icon: 'none' }); }
      });
    }
  },
  onLoginClose() { wx.navigateBack(); },
  onLogin() { wx.redirectTo({ url: '/pages/login/login' }); }
});
