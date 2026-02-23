package event

import (
	"context"
	"encoding/json"
	"strings"

	"inventory-service/internal/middleware"

	"github.com/redis/go-redis/v9"
)

type orderEventPayload struct {
	EventType     string `json:"eventType"`
	OrderID       string `json:"orderId"`
	ProductID     string `json:"productId"`
	Quantity      int    `json:"quantity"`
	Status        string `json:"status"`
	CorrelationID string `json:"correlationId"`
}

type Subscriber struct {
	client  *redis.Client
	pubsub  *redis.PubSub
	channel string
}

func StartOrderEventSubscriber(ctx context.Context, redisAddr string, channel string) (*Subscriber, error) {
	addr := strings.TrimSpace(redisAddr)
	if addr == "" {
		addr = "redis:6379"
	}

	name := strings.TrimSpace(channel)
	if name == "" {
		name = "orders.events"
	}

	client := redis.NewClient(&redis.Options{
		Addr: addr,
	})

	if err := client.Ping(ctx).Err(); err != nil {
		_ = client.Close()
		return nil, err
	}

	pubsub := client.Subscribe(ctx, name)
	if _, err := pubsub.Receive(ctx); err != nil {
		_ = pubsub.Close()
		_ = client.Close()
		return nil, err
	}

	sub := &Subscriber{
		client:  client,
		pubsub:  pubsub,
		channel: name,
	}

	go sub.listen(ctx)
	middleware.LogJSON("info", "inventory.event.subscriber_started", map[string]any{
		"channel": name,
		"address": addr,
	})

	return sub, nil
}

func (s *Subscriber) Close() {
	if s == nil {
		return
	}
	if s.pubsub != nil {
		_ = s.pubsub.Close()
	}
	if s.client != nil {
		_ = s.client.Close()
	}
}

func (s *Subscriber) listen(ctx context.Context) {
	channel := s.pubsub.Channel()

	for {
		select {
		case <-ctx.Done():
			return
		case msg, ok := <-channel:
			if !ok {
				return
			}
			s.handle(msg.Payload)
		}
	}
}

func (s *Subscriber) handle(raw string) {
	payload := orderEventPayload{}
	if err := json.Unmarshal([]byte(raw), &payload); err != nil {
		middleware.LogJSON("warn", "inventory.event.decode_failed", map[string]any{
			"channel": s.channel,
			"error":   err.Error(),
		})
		return
	}

	middleware.LogJSON("info", "inventory.event.consumed", map[string]any{
		"channel":        s.channel,
		"event_type":     payload.EventType,
		"order_id":       payload.OrderID,
		"product_id":     payload.ProductID,
		"quantity":       payload.Quantity,
		"status":         payload.Status,
		"correlation_id": payload.CorrelationID,
	})
}
