# AT Dialer — Android App

אפליקציית Android לחיוג אוטומטי, תזמון שיחות ולוג מסודר.

## פיצ'רים
- 📞 **חיוג מהיר** — הזן מספר ושם, לחץ חייג
- 👥 **אנשי קשר** — שמור מספרים לשימוש חוזר
- ⏰ **תזמון שיחות** — קבע שעה + ימים לחיוג אוטומטי (WorkManager, שורד reboot)
- 📊 **לוג מלא** — כל שיחה עם תוצאה, משך, תאריך ושעה
- 📈 **סטטיסטיקות** — נענו / לא נענו / תפוס / ממוצע זמן
- 📤 **ייצוא CSV** — שלח את הלוג לאקסל/מייל

## דרישות
- Android 8.0+ (API 26)
- הרשאות: CALL_PHONE, READ_CALL_LOG, READ_PHONE_STATE

## בנייה עם Android Studio

1. פתח Android Studio (Hedgehog 2023.1.1+)
2. **File → Open** → בחר את תיקיית `ATDialer`
3. המתן ל-Gradle sync
4. חבר טלפון Android ב-USB (הפעל Developer Options + USB Debugging)
5. לחץ **▶ Run**

## בנייה עם Command Line

```bash
# ודא שיש ANDROID_HOME מוגדר
export ANDROID_HOME=~/Library/Android/sdk   # macOS
export ANDROID_HOME=~/Android/Sdk           # Linux
export PATH=$PATH:$ANDROID_HOME/platform-tools

cd ATDialer
./gradlew assembleDebug

# ה-APK יהיה ב:
# app/build/outputs/apk/debug/app-debug.apk

# התקנה ישירה:
adb install app/build/outputs/apk/debug/app-debug.apk
```

## הגדרות נדרשות בטלפון

1. **הגדרות → אפליקציות → AT Dialer → הרשאות**
   - אשר: טלפון, יומן שיחות
2. **הגדרות → אפליקציות → AT Dialer → סוללה**
   - בחר "ללא הגבלה" (כדי שתזמון יעבוד ברקע)

## מבנה הקוד

```
app/src/main/java/com/atdialer/app/
├── MainActivity.kt           # פעילות ראשית + ניווט
├── MainViewModel.kt          # ViewModel משותף
├── data/
│   └── Database.kt           # Room DB: Contact, Schedule, CallLogEntry
├── service/
│   ├── DialerService.kt      # Foreground service לחיוג ומעקב שיחות
│   ├── ScheduleWorker.kt     # WorkManager worker לתזמון
├── ui/
│   ├── contacts/DialerFragment.kt   # מסך חייגן + אנשי קשר
│   ├── schedule/ScheduleFragment.kt # מסך תזמון
│   └── log/LogFragment.kt           # מסך לוג + סטטיסטיקות
```

## הערות חשובות

- **ללא root**: האפליקציה משתמשת ב-`Intent.ACTION_CALL` הרשמי של Android
- **תזמון מדויק**: WorkManager מבטיח הפעלה גם אחרי reboot
- **מעקב שיחות**: `TelephonyManager` מזהה מצב שיחה (ענה/לא ענה/תפוס)
- **AT Commands**: Android חוסם גישה ישירה ל-modem ב-userspace ללא root
