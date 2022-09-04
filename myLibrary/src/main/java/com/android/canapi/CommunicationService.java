package com.android.canapi;

/**
 * Updated on 2022/03/06
 */

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import static com.android.canapi.Command.Send.sendData;

public class CommunicationService {
    private static CommunicationService sCommunicationService;
    private static Context mActivity;
    private IProcessData mIProcessData;
    private static int mCountTime = 15000;

    public interface IProcessData {
        void process(byte[] data, DataType type);
    }

    /**
     * get data
     *
     * @param iProcessData
     */
    public void getData(IProcessData iProcessData) {
        mIProcessData = iProcessData;
    }

    private CommunicationService() {
    }

    public static CommunicationService getInstance(Context activity) {
        if (sCommunicationService == null) {
            sCommunicationService = new CommunicationService();
            mActivity = activity;
        }
        return sCommunicationService;
    }

    private static final String IntentAction = "com.android.intent.action.SHUTDOWN";

    /**
     * Setting  the shutdown countdown
     * @param second second(10~30S)
     */
    public void setShutdownCountTime(int second) {
        if (second < 10 || second > 30) {
            mCountTime = 10 * 1000;
        } else {
            mCountTime = second * 1000;
        }
        Intent intent = new Intent();
        intent.setAction(IntentAction);
        intent.putExtra("shutdown_value", "shutdown_time");
        intent.putExtra("shutdown_time", mCountTime);
        mActivity.sendBroadcast(intent);
    }

    /**
     * send data
     *
     * @param data
     */
    public void send(byte[] data) {
        if (mService != null) {
            try {
//                Log.i("gh0st", "sdk :" + saveHex2String(data));
                mService.handleData(data);
            } catch (RemoteException e) {
                Log.e("gh0st", e.getMessage(), e);
                e.printStackTrace();
            }
        }
    }

    private byte[] byteArrayAddByteArray(byte ch_num, int id, byte[] data, FrameFormat frameFormat) {

        byte[] resultData = new byte[4 + data.length + 2];
        resultData[0] = ch_num;
        switch (frameFormat) {
            case stdFormat:
//                int stdTrans = (((id << 4) >> 3) << 4);
                int stdTrans = id << 5;
                byte[] stdId = new byte[4];
                stdId[0] = (byte) (stdTrans >> 8 & 0xff);
                stdId[1] = (byte) (stdTrans      & 0xff);
                stdId[2] = (byte) 0x00;
                stdId[3] = (byte) 0x00;
                System.arraycopy(stdId, 0, resultData, 1, 4);
                resultData[4 + 1] = (byte) data.length;
                System.arraycopy(data, 0, resultData, 4 + 2, data.length);
                break;
            case extFormat:
                int extTrans = (id << 3) + 4;
                byte[] extId = new byte[4];
                extId[0] = (byte) (extTrans >> 24 & 0xff);
                extId[1] = (byte) (extTrans >> 16 & 0xff);
                extId[2] = (byte) (extTrans >>  8 & 0xff);
                extId[3] = (byte) (extTrans       & 0xff);
                System.arraycopy(extId, 0, resultData, 1, 4);
                resultData[4 + 1] = (byte) data.length;
                System.arraycopy(data, 0, resultData, 4 + 2, data.length);
                break;
        }

        return resultData;
    }

    public void sendCan(byte ch_mun, int id, byte[] data, FrameFormat frameFormat) {
        send(sendData(byteArrayAddByteArray(ch_mun, id, data, frameFormat), Command.SendDataType.Can));
    }

    // 原生
    private byte[] byteArrayAddByteArray(byte ch_num, byte[] id, byte[] data) {
        byte[] resultData = new byte[id.length + data.length + 2];
        resultData[0] = ch_num;
        id[3] = (byte) (((id[1]  << 5) & 0xff) | (byte)((id[0] << 3) & 0xff));
        id[2] = (byte) ((id[0]  << 5) & 0xff);
        id[1] = 0x00;
        id[0] = 0x00;
        System.arraycopy(id, 0, resultData, 1, id.length);
        resultData[id.length + 1] = (byte) data.length;
        System.arraycopy(data, 0, resultData, id.length + 2, data.length);
        return resultData;
    }

    public void sendCan(byte ch_mun, byte[] id, byte[] data) {
        send(sendData(byteArrayAddByteArray(ch_mun, id, data), Command.SendDataType.Can));
    }

    public void sendOBD(byte[] data) {
        send(sendData(data, Command.SendDataType.OBDII));
    }

    public void sendJ1939(byte[] data) {
        send(sendData(data, Command.SendDataType.J1939));
    }


    /**
     * bind service
     * if bind fail throws Exception
     *
     * @throws Exception
     */
    public boolean bind() throws Exception {
        if (mActivity != null) {
            Intent intent = new Intent();
            intent.setAction("com.android.guard.E9631Service");
            intent.setPackage("com.android.guard");
            return mActivity.bindService(intent, conn, Context.BIND_AUTO_CREATE);
        } else {
            throw new Exception("gh0st -- bind fail , activity is Null");
        }
    }

    /**
     * unbind service
     * if unbind fail throw Exception
     *
     * @throws Exception
     */
    public void unbind() throws Exception {
        if (mActivity != null) {
            if (mService != null) {
                try {
                    mService.unregisterCallback(mCallback);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                mActivity.unbindService(conn);
                mService = null;
            }
        } else {
            throw new Exception("gh0st -- unbind fail , activity is Null");
        }
    }

    public boolean isBindSuccess() {
        return mService != null;
    }

    IRemoteService mService;
    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                mService = IRemoteService.Stub.asInterface(service);
                mService.registerCallback(mCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };
    /**
     * Զ�˷���ص�
     */
    private IRemoteServiceCallBack mCallback = new IRemoteServiceCallBack.Stub() {

        @Override
        public void valueChanged(byte[] protocolData) throws RemoteException {
            postData2(protocolData);
        }
    };

    private void postData2(byte[] protocolData) {
        byte type = protocolData[0];//Э����������
        int length = ((protocolData[1] & 0xFF) << 8 | ((protocolData[2] & 0xFF)));
        byte dataType = protocolData[3];//Э����������
        byte[] data = new byte[length];//�����ݲ���
        System.arraycopy(protocolData, 3, data, 0, data.length);//ȡ��ÿһ�������ݲ���
        if (type == 0x21) {
            mIProcessData.process(data, DataType.TMcuVersion);
        } else if (type == 0x30 && dataType == 0x01) {//acc on
            mIProcessData.process(data, DataType.TAccOn);
        } else if (type == 0x30 && dataType == 0x00) {//acc off
            mIProcessData.process(data, DataType.TAccOff);
        } else if (type == 0x31 && dataType == 0x12) {//can 125
            mIProcessData.process(data, DataType.TCan125);
        } else if (type == 0x31 && dataType == 0x25) {//can 250
            mIProcessData.process(data, DataType.TCan250);
        } else if (type == 0x31 && dataType == 0x50) {//can 500
            mIProcessData.process(data, DataType.TCan500);
        } else if (type == 0x33) {
            mIProcessData.process(data, DataType.TMcuVoltage);
        } else if (type == 0x41) {
            mIProcessData.process(data, DataType.TDataCan);
        } else if (type == 0x61) {
            mIProcessData.process(data, DataType.TDataOBD);
        } else if (type == 0x71) {
            mIProcessData.process(data, DataType.TDataJ1939);
        } else if (type == (byte) 0x81) {
            mIProcessData.process(data, DataType.TDataMode);
        } else if (type == (byte) 0x83) {
            mIProcessData.process(data, DataType.TChannel);
        } else if (type == (byte) (0x91)) {
            mIProcessData.process(data, DataType.TGPIO);
        } else if (type == 0x50) {
            mIProcessData.process(data, DataType.TFilter);
        } else if (type == 0x51) {
            mIProcessData.process(data, DataType.TCancelFilter);
        } else {
            mIProcessData.process(data, DataType.TUnknow);
            Log.d("gh0st", "we don't support:" + saveHex2String(protocolData));
        }
    }

    public static String saveHex2String(byte[] data) {
        StringBuilder sb = new StringBuilder();
        final char[] HEX = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        for (byte aData : data) {
            int value = aData & 0xff;
            sb.append(HEX[value / 16]).append(HEX[value % 16]).append(" ");
        }
        return sb.toString();
    }
}
