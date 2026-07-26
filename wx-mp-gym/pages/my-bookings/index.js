var app = getApp(); var request = require('../../utils/request.js'); Page({   goBack() { wx.navigateBack(); },   data: { filter: 'booked', list: [], showCodeInput: false, codeClassId: null, checkinCode: '', codeLoading: false },   onShow() { this.loadList(); },   onFilter(e) {     var f = e.currentTarget.dataset.filter;     this.setData({ filter: f });     this.loadList();   },   loadList() {     var that = this;     if (!app.isLoggedIn()) { that.setData({ list: [] }); return; }     request.get('/api/member/bookings', { status: that.data.filter }, function(res) {       var list = res.list || res || [];             list.forEach(function(item) {
        if (item.bookingTime) item.bookingTime = String(item.bookingTime).replace('T', ' ');
        if (item.type === 'group') {
          var st = item.classStartTime, et = item.classEndTime;
          if (st) {
            var sd = new Date(String(st).replace(/-/g, '/'));
            if (!isNaN(sd.getTime())) {
              var mm = String(sd.getMonth()+1).padStart(2,'0');
              var dd = String(sd.getDate()).padStart(2,'0');
              var sh = String(sd.getHours()).padStart(2,'0');
              var sm = String(sd.getMinutes()).padStart(2,'0');
              if (et) {
                var ed = new Date(String(et).replace(/-/g, '/'));
                if (!isNaN(ed.getTime())) {
                  var eh = String(ed.getHours()).padStart(2,'0');
                  var em = String(ed.getMinutes()).padStart(2,'0');
                  item.classTime = mm+'/'+dd+' '+sh+':'+sm+'-'+eh+':'+em;
                } else { item.classTime = mm+'/'+dd+' '+sh+':'+sm; }
              } else { item.classTime = mm+'/'+dd+' '+sh+':'+sm; }
            } else { item.classTime = String(st); }
          } else { item.classTime = ''; }
        } else if (item.type === 'pt' && item.bookingTime) {
          item.classTime = item.bookingTime;
        }
      });       that.setData({ list: list });     });   },   statusText: function(s) {     var map = { booked: '已预约', checked_in: '已签到', completed: '已完成', cancelled: '已取消', scheduled: '待上课' };     return map[s] || s;   },   goDetail: function(e) {     var item = e.currentTarget.dataset;     if (item.type === 'pt') {       wx.navigateTo({ url: '/pages/coach-detail/index?trainerId=' + item.trainerId });     } else {       wx.navigateTo({ url: '/pages/booking/index?id=' + item.classId });     }   },   onCancel: function(e) {     var item = e.currentTarget.dataset;     var that = this;     wx.showModal({ title: '确认取消', content: '确定取消该预约吗？', success: function(r) {       if (!r.confirm) return;       if (item.type === 'pt') {         request.put('/api/personal-trainings/' + item.id, { status: 'cancelled' }, function(res) {           if (res.success) { wx.showToast({ title: '已取消', icon: 'success' }); that.loadList(); }           else { wx.showToast({ title: res.message || '取消失败', icon: 'none' }); }         });       } else {         request.post('/api/class-bookings/' + item.id + '/cancel', {}, function(res) {           if (res.success) { wx.showToast({ title: '已取消', icon: 'success' }); that.loadList(); }           else { wx.showToast({ title: res.message || '取消失败', icon: 'none' }); }         });       }     }});     return false;   },   onCheckin: function(e) {     var item = e.currentTarget.dataset;     if (item.type === 'pt') {       wx.showToast({ title: '私教课签到请联系您的教练，由教练端进行人脸识别签到', icon: 'none' });       return;     }     this.setData({ showCodeInput: true, codeClassId: item.id, checkinCode: '' });   },   onCodeInput: function(e) {     this.setData({ checkinCode: e.detail.value });   },   submitCheckinCode: function() {     var that = this;     var memberId = (app.globalData.userInfo || {}).memberId;     if (!memberId) { wx.showToast({ title: '请先登录', icon: 'none' }); return; }     if (!that.data.checkinCode || that.data.checkinCode.length !== 6) {       wx.showToast({ title: '请输入6位签到码', icon: 'none' }); return;     }     that.setData({ codeLoading: true });     request.post('/api/check-in/class/' + that.data.codeClassId + '/verify-code?memberId=' + memberId + '&code=' + that.data.checkinCode, {}, function(r) {       that.setData({ codeLoading: false });       if (r && r.success) {         wx.showToast({ title: '签到成功', icon: 'success' });         that.setData({ showCodeInput: false });         that.loadList();       } else {         wx.showToast({ title: (r && r.message) || '签到失败', icon: 'none' });       }     });   },   hideCodeInput: function() {     this.setData({ showCodeInput: false });   },   stopPropagation: function() {} });