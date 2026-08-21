package com.amicbeam.beyondcraftlines.common.network;

/** Pure page-order guard shared by the proposal upload state machine. */
final class ProposalPageSequence
{
    private final int pageCount;
    private int nextPage;

    ProposalPageSequence(int pageCount, int maxPages)
    {
        if (pageCount < 1 || pageCount > maxPages) throw new IllegalArgumentException("invalid proposal page count");
        this.pageCount = pageCount;
    }

    void accept(int page)
    {
        if (page != nextPage || page < 0 || page >= pageCount)
            throw new IllegalArgumentException("invalid proposal page sequence");
        nextPage++;
    }

    int nextPage() { return nextPage; }
    boolean complete() { return nextPage == pageCount; }
}
