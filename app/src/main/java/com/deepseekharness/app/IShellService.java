package com.deepseekharness.app;

/**
 * 手写的 AIDL 生成物，等价于 src/main/aidl/com/deepseekharness/app/IShellService.aidl 的编译结果。
 *
 * 原因：Termux 上 SDK build-tools 的 aidl 是 x86-64 ELF，无法在 arm64 设备上执行，
 * 且 AGP 没有 aidl 的覆盖开关；因此 build.gradle.kts 关闭了 aidl，改用本文件。
 *
 * 事务号契约（不可改动）：
 *  - exec              = FIRST_CALL_TRANSACTION + 0
 *  - execVirtualScreen = FIRST_CALL_TRANSACTION + 1
 *  - destroy           = FIRST_CALL_TRANSACTION + 16777113 = 16777114（Shizuku 约定的销毁事务）
 * 修改 .aidl 时必须同步修改本文件。
 */
public interface IShellService extends android.os.IInterface {
    String DESCRIPTOR = "com.deepseekharness.app.IShellService";

    String exec(String cmd) throws android.os.RemoteException;

    /** Native-only typed launcher; generic shell text may not invoke app_process. */
    String execVirtualScreen(String command) throws android.os.RemoteException;

    /** Shizuku 约定的销毁事务；升级或解绑时退出旧的特权服务进程。 */
    void destroy() throws android.os.RemoteException;

    /** 默认空实现（与 aidl 生成物一致）。 */
    class Default implements IShellService {
        @Override public String exec(String cmd) { return null; }
        @Override public String execVirtualScreen(String command) { return null; }
        @Override public void destroy() { }
        @Override public android.os.IBinder asBinder() { return null; }
    }

    abstract class Stub extends android.os.Binder implements IShellService {
        static final int TRANSACTION_exec = android.os.IBinder.FIRST_CALL_TRANSACTION + 0;
        static final int TRANSACTION_execVirtualScreen = android.os.IBinder.FIRST_CALL_TRANSACTION + 1;
        static final int TRANSACTION_destroy = android.os.IBinder.FIRST_CALL_TRANSACTION + 16777113;

        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        public static IShellService asInterface(android.os.IBinder obj) {
            if (obj == null) return null;
            android.os.IInterface local = obj.queryLocalInterface(DESCRIPTOR);
            if (local instanceof IShellService) return (IShellService) local;
            return new Proxy(obj);
        }

        @Override
        public android.os.IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)
                throws android.os.RemoteException {
            if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION
                    && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
                data.enforceInterface(DESCRIPTOR);
            }
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DESCRIPTOR);
                return true;
            }
            switch (code) {
                case TRANSACTION_exec: {
                    String arg0 = data.readString();
                    String result = this.exec(arg0);
                    reply.writeNoException();
                    reply.writeString(result);
                    return true;
                }
                case TRANSACTION_execVirtualScreen: {
                    String arg0 = data.readString();
                    String result = this.execVirtualScreen(arg0);
                    reply.writeNoException();
                    reply.writeString(result);
                    return true;
                }
                case TRANSACTION_destroy: {
                    this.destroy();
                    reply.writeNoException();
                    return true;
                }
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static class Proxy implements IShellService {
            private final android.os.IBinder remote;

            Proxy(android.os.IBinder remote) {
                this.remote = remote;
            }

            @Override
            public android.os.IBinder asBinder() {
                return remote;
            }

            public String getInterfaceDescriptor() {
                return DESCRIPTOR;
            }

            @Override
            public String exec(String cmd) throws android.os.RemoteException {
                return callString(TRANSACTION_exec, cmd);
            }

            @Override
            public String execVirtualScreen(String command) throws android.os.RemoteException {
                return callString(TRANSACTION_execVirtualScreen, command);
            }

            @Override
            public void destroy() throws android.os.RemoteException {
                android.os.Parcel data = android.os.Parcel.obtain();
                android.os.Parcel reply = android.os.Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    remote.transact(Stub.TRANSACTION_destroy, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            private String callString(int code, String arg) throws android.os.RemoteException {
                android.os.Parcel data = android.os.Parcel.obtain();
                android.os.Parcel reply = android.os.Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeString(arg);
                    remote.transact(code, data, reply, 0);
                    reply.readException();
                    return reply.readString();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
        }
    }
}
