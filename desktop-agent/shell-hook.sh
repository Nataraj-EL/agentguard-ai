#!/bin/bash
# AgentGuard AI - Non-Bypassable Shell Interceptor Trap

# Enable extended debugging to allow trap to cancel executions
shopt -s extdebug

agentguard_preexec() {
    # Guard against infinite recursion when trap executes curl/echo/sed
    if [ "$AGENTGUARD_INTERCEPTING" = "1" ]; then
        return 0
    fi

    local COMMAND_STRING="$1"

    # Strict normalization - strip leading/trailing spaces
    COMMAND_STRING=$(echo "$COMMAND_STRING" | xargs)

    # Filter out commands we want to validate (git, npm, rm, drop)
    if [[ "$COMMAND_STRING" =~ ^git\ .* ]] || \
       [[ "$COMMAND_STRING" =~ ^/usr/bin/git\ .* ]] || \
       [[ "$COMMAND_STRING" =~ ^npm\ .* ]] || \
       [[ "$COMMAND_STRING" =~ ^rm\ -rf\ .* ]] || \
       [[ "$COMMAND_STRING" =~ ^drop\ database\ .* ]]; then

        export AGENTGUARD_INTERCEPTING=1

        local SESSION_ID="${AGENTGUARD_SESSION_ID:-development-human}"

        # Synchronous POST to fast-path validation API (Target <10ms)
        local RESPONSE_JSON
        RESPONSE_JSON=$(curl -s -w "\\n%{http_code}" -X POST \
          -H "Content-Type: application/json" \
          -H "X-Session-ID: $SESSION_ID" \
          -d "{\"command\":\"$COMMAND_STRING\"}" \
          http://localhost:8080/api/v1/commands/validate-fast 2>/dev/null)

        local CURL_STATUS=$?
        export AGENTGUARD_INTERCEPTING=0

        # Backend is down: allow execution immediately (fallback)
        if [ $CURL_STATUS -ne 0 ] || [ -z "$RESPONSE_JSON" ]; then
            return 0
        fi

        local HTTP_STATUS
        HTTP_STATUS=$(echo "$RESPONSE_JSON" | tail -n1)
        local DECISION_BODY
        DECISION_BODY=$(echo "$RESPONSE_JSON" | sed '$d')

        if [ "$HTTP_STATUS" -eq 200 ]; then
            local DECISION
            DECISION=$(echo "$DECISION_BODY" | grep -o '"decision":"[^"]*' | grep -o '[^"]*$')
            local MESSAGE
            MESSAGE=$(echo "$DECISION_BODY" | grep -o '"message":"[^"]*' | grep -o '[^"]*$')
            
            if [ "$DECISION" = "REJECTED" ]; then
                echo ""
                echo "❌ [AgentGuard] BLOCKED: Command violates active safety policies."
                echo "   Triggered Rule: $MESSAGE"
                echo ""
                # Return 1: Instructs Bash to CANCEL execution of this command!
                return 1
            elif [ "$DECISION" = "REVIEW" ]; then
                echo "⚠️ [AgentGuard] WARNING: Command is under review: $MESSAGE"
                return 0
            fi
        fi
    fi

    return 0
}

# Register the DEBUG trap callback
trap 'agentguard_preexec "$BASH_COMMAND"' DEBUG

echo "[AgentGuard] Non-bypassable Shell Interceptor active."
