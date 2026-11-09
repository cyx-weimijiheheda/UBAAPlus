package cn.edu.ubaa.plus.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 定时提醒的闹钟出口。
 *
 * 用 AlarmManager 代替常驻前台服务：不再需要那条杀不掉的通知，用户只在真有课要上、真有签到或作业时才看到推送。 收到闹钟后启动一次性的后台检查，跑完就结束。
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    ReminderScheduler.scheduleNext(context)
    ReminderCheckWorker.runOnce(context)
  }
}
