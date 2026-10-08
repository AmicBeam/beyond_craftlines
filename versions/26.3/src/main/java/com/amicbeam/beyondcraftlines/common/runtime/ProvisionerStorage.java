package com.amicbeam.beyondcraftlines.common.runtime;

import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import org.jetbrains.annotations.NotNull;

/** Dynamic BD resource storage whose insertion gate can only be opened by an order transaction. */
public final class ProvisionerStorage extends UnifiedStorage
{
    private final Runnable changeListener;
    private boolean acceptingOrder;

    public ProvisionerStorage(Runnable changeListener)
    {
        super(null, AbstractUnorderedStackHandler.UiTimestampPolicy.AUTO);
        this.changeListener = changeListener;
    }

    @Override
    public @NotNull KeyAmount insert(IStackKey<?> key, long amount, boolean simulate)
    {
        if (!acceptingOrder) return new KeyAmount(key, Math.max(0, amount));
        return super.insert(key, amount, simulate);
    }

    public KeyAmount insertFromOrder(IStackKey<?> key, long amount, boolean simulate)
    {
        acceptingOrder = true;
        try { return insert(key, amount, simulate); }
        finally { acceptingOrder = false; }
    }

    @Override
    public long setAmountByKey(IStackKey<?> key, long amount)
    {
        if (!acceptingOrder) return getStackByKey(key).amount();
        return super.setAmountByKey(key, amount);
    }

    @Override
    public void setStackDirectly(int slot, IStackKey<?> key, long amount)
    {
        if (acceptingOrder) super.setStackDirectly(slot, key, amount);
    }

    @Override
    public void onChange()
    {
        super.onChange();
        if (changeListener != null) changeListener.run();
    }
}
