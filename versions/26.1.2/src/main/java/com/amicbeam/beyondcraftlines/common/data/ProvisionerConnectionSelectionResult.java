package com.amicbeam.beyondcraftlines.common.data;

public enum ProvisionerConnectionSelectionResult
{
    SELECTED("message.beyond_craftlines.provisioner_connection_mode_selected"),
    NO_ENABLED_RECIPE_TYPES("error.beyond_craftlines.provisioner_connection_no_recipes"),
    INVALID_NETWORK("error.beyond_craftlines.provisioner_selection_failed");

    private final String messageKey;

    ProvisionerConnectionSelectionResult(String messageKey)
    { this.messageKey = messageKey; }

    public String messageKey()
    { return messageKey; }
}
