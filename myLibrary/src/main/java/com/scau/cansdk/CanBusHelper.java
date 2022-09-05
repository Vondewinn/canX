package com.scau.cansdk;

import android.content.Context;
import android.util.Log;
import com.android.canapi.CommunicationService;
import com.android.canapi.DataType;
import com.android.canapi.FrameFormat;

/*
* @author Vondewinn
* @date 2022-09-04
* */

public class CanBusHelper {

    private ProcessData mPorcessData;
    private static Context mContext;
    private static CanBusHelper canBusHelper;
    private static CommunicationService mService;
    private boolean _isOpen = false;

    private CanBusHelper(){

    }

    // 单例模式
    public static CanBusHelper getInstance(Context context) {
        if (canBusHelper == null) {
            canBusHelper = new CanBusHelper();
            mContext = context;
        }
        return canBusHelper;
    }

    /*
     * @fun: open CAN bus
     */
    public void open(){
        try {
            mService = CommunicationService.getInstance(mContext);
            mService.setShutdownCountTime(12);
            mService.bind();
            dataReceiveBuffer();
            _isOpen = true;
            Log.e("cansdk", "CAN START!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("cansdk", e.getMessage());
        }
    }

    public boolean isOpen() {
        if (_isOpen) return true;
        else return false;
    }

    /*
     * @fun : 命令设置方法
     */
    public static void sendCommand(byte[] data){
        if (mService != null) {
            if (mService.isBindSuccess()) {
                mService.send(data);
            }
        }
    }

    /*
     * @fun    : 发送数据
     * @param  : stdFormat为标准帧， extFormat为扩展帧
     * @notes  : 默认为通道1发送
     */
    public static void sendCanData(int id, byte[] data, FrameFormat frameFormat) {
        if (mService != null) {
            if (mService.isBindSuccess()) {
                mService.sendCan((byte) 0x01, id, data, frameFormat);
            }
        }
    }

    // 接口回调
    public void getData(ProcessData processData) {
        this.mPorcessData = processData;
    }

    /*
     * @fun: 私有数据接收缓冲区， 用以实时接收数据
     */
    public void dataReceiveBuffer() {
        if (mService != null) {
            mService.getData(new CommunicationService.IProcessData() {
                @Override
                public void process(byte[] data, DataType type) {
                    switch (type){
                        case TDataCan:
                            mPorcessData.process(data, type);
                            break;
                        case TCan500:
                            Log.i("cansdk", "process: set 500K success");
                            break;
                        case TCan250:
                            Log.i("cansdk", "process: set 250K success");
                            break;
                    }
                }
            });
        }
    }

    /*
     * @fun: close CAN bus
     */
    public void close() {
        if (mService != null) {
            try {
                _isOpen = false;
                mService.unbind();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
