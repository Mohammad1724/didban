package main

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"net/http"
	"time"
)

// auditDestructive records one bounded, credential-free event for every
// admitted destructive operation. It never records headers, bodies, query
// strings, container names, scripts, or service output.
func (a *API) auditDestructive(operation string, next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		started := time.Now()
		next(rec, r)

		keyHash := sha256.Sum256([]byte(r.Header.Get("X-Idempotency-Key")))
		detail := fmt.Sprintf(
			"operation=%s status=%d client=%s key_sha256=%s duration_ms=%d",
			operation,
			rec.status,
			clientIP(r),
			hex.EncodeToString(keyHash[:8]),
			time.Since(started).Milliseconds(),
		)
		a.mon.RecordEventLocal(Event{
			Time:   time.Now(),
			Type:   "destructive_api_operation",
			Detail: detail,
		})
	}
}
