package main

import (
	"net"
	"sync"
)

// maxActiveConnections bounds sockets that have been accepted but not closed.
// Combined with the server timeouts, this caps Slowloris/TLS-handshake resource
// use without affecting the normal phone client and status checks.
const maxActiveConnections = 256

type limitedListener struct {
	net.Listener
	slots     chan struct{}
	done      chan struct{}
	closeOnce sync.Once
}

func newLimitedListener(inner net.Listener, limit int) net.Listener {
	if limit <= 0 {
		panic("connection limit must be positive")
	}
	return &limitedListener{Listener: inner, slots: make(chan struct{}, limit), done: make(chan struct{})}
}

func (l *limitedListener) Accept() (net.Conn, error) {
	select {
	case l.slots <- struct{}{}:
	case <-l.done:
		return nil, net.ErrClosed
	}
	conn, err := l.Listener.Accept()
	if err != nil {
		<-l.slots
		return nil, err
	}
	return &limitedConn{Conn: conn, release: func() { <-l.slots }}, nil
}

func (l *limitedListener) Close() error {
	l.closeOnce.Do(func() { close(l.done) })
	return l.Listener.Close()
}

type limitedConn struct {
	net.Conn
	once    sync.Once
	release func()
}

func (c *limitedConn) Close() error {
	err := c.Conn.Close()
	c.once.Do(c.release)
	return err
}
