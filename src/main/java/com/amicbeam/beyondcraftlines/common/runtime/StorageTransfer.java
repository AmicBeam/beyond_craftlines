package com.amicbeam.beyondcraftlines.common.runtime;

final class StorageTransfer
{
    private StorageTransfer() {}

    static boolean isComplete(long requested, long transferred)
    {
        return requested >= 0 && transferred == requested;
    }
}
