package com.chichu.toolbox.acceptance;

import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;

public class ProbeUserService extends Binder {
    public ProbeUserService() {}

    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == 1) {
            reply.writeNoException();
            reply.writeInt(android.os.Process.myUid());
            return true;
        }
        if (code == 16777115) { System.exit(0); return true; }
        return super.onTransact(code, data, reply, flags);
    }
}
