package main

import (
	"crypto/rand"
	"io"
	"net/http"
	"strconv"
)

// Real bandwidth test endpoints. The Android app streams from
// /api/bandwidth/download and pushes to /api/bandwidth/upload to measure the
// node's genuine upload/download throughput (replacing the app's previous
// simulated numbers).

const (
	bandwidthDefaultBytes = 10 * 1024 * 1024  // default payload: 10 MiB
	bandwidthMaxBytes     = 200 * 1024 * 1024 // hard cap per request: 200 MiB
	bandwidthBlockSize    = 64 * 1024         // flush chunk: 64 KiB
)

// handleBandwidthDownload streams [bytes] of pseudo-random data so the client
// can measure real download throughput of this node.
func (a *API) handleBandwidthDownload(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}

	n := bandwidthDefaultBytes
	if v := r.URL.Query().Get("bytes"); v != "" {
		parsed, err := strconv.ParseInt(v, 10, 64)
		if err != nil || parsed < bandwidthBlockSize {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "bytes must be >= 65536"})
			return
		}
		n = int(parsed)
		if n > bandwidthMaxBytes {
			n = bandwidthMaxBytes
		}
	}

	// One random block, repeated: defeats caching without paying crypto/rand
	// overhead for the whole payload.
	buf := make([]byte, bandwidthBlockSize)
	if _, err := io.ReadFull(rand.Reader, buf); err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "rng failure"})
		return
	}

	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("Content-Length", strconv.Itoa(n))
	w.WriteHeader(http.StatusOK)

	fl, canFlush := w.(http.Flusher)
	remaining := n
	for remaining > 0 {
		chunk := remaining
		if chunk > bandwidthBlockSize {
			chunk = bandwidthBlockSize
		}
		if _, err := w.Write(buf[:chunk]); err != nil {
			return // client disconnected
		}
		if canFlush {
			fl.Flush()
		}
		remaining -= chunk
	}
}

// handleBandwidthUpload consumes (and discards) the request body and reports
// the byte count, so the client can measure real upload throughput.
func (a *API) handleBandwidthUpload(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "method not allowed"})
		return
	}

	// Cap the body so a (stolen) token cannot be used to exhaust disk/CPU.
	r.Body = http.MaxBytesReader(w, r.Body, bandwidthMaxBytes+1024*1024)
	n, err := io.Copy(io.Discard, r.Body)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "failed to read body: " + err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"received_bytes": n})
}
