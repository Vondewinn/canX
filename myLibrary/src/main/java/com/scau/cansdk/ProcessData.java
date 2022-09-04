package com.scau.cansdk;

import com.android.canapi.DataType;

public interface ProcessData {

    void process(byte[] bytes, DataType dataType);

}
