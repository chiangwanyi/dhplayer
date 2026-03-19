package com.jwy.dhplayer.util;

import com.netsdk.lib.NetSDKLib;

import java.util.Calendar;
import java.util.Date;

public class DahuaDateUtil {

    public static NetSDKLib.NET_TIME getNetTime(Date date) {
        Calendar startCal = Calendar.getInstance();
        startCal.setTime(date);

        NetSDKLib.NET_TIME netTime = new NetSDKLib.NET_TIME();
        netTime.dwYear = startCal.get(Calendar.YEAR);
        netTime.dwMonth = startCal.get(Calendar.MONTH) + 1;
        netTime.dwDay = startCal.get(Calendar.DAY_OF_MONTH);
        netTime.dwHour = startCal.get(Calendar.HOUR_OF_DAY);
        netTime.dwMinute = startCal.get(Calendar.MINUTE);
        netTime.dwSecond = startCal.get(Calendar.SECOND);

        return netTime;
    }
}
