
import { NativeModules } from 'react-native';
import base64 from 'base64-js';
import { defineCustomEventTarget } from 'event-target-shim';
import MessageEvent from './MessageEvent';
import RTCDataChannelEvent from './RTCDataChannelEvent';
import EventEmitter from './EventEmitter';

const { WebRTCModule } = NativeModules;

type RTCDataChannelState = 'connecting' | 'open' | 'closing' | 'closed';

const DATA_CHANNEL_EVENTS = ['open', 'message', 'bufferedamountlow', 'closing', 'close', 'error'];

export default class RTCDataChannel extends defineCustomEventTarget(...DATA_CHANNEL_EVENTS) {
    _peerConnectionId: number;
    _reactTag: string;

  _bufferedAmount: number;
  _id: number;
  _label: string;
  _maxPacketLifeTime: ?number;
  _maxRetransmits: ?number;
  _negotiated: boolean;
  _ordered: boolean;
  _protocol: string;
  _readyState: RTCDataChannelState;

  binaryType: 'arraybuffer' = 'arraybuffer'; // we only support 'arraybuffer'
  bufferedAmountLowThreshold: number = 0;

    constructor(info) {
        super();

        this._peerConnectionId = info.peerConnectionId;
        this._reactTag = info.reactTag;

        this._label = info.label;
        this._id = info.id === -1 ? null : info.id; // null until negotiated.
        this._ordered = Boolean(info.ordered);
        this._maxPacketLifeTime = info.maxPacketLifeTime;
        this._maxRetransmits = info.maxRetransmits;
        this._protocol = info.protocol || '';
        this._negotiated = Boolean(info.negotiated);
        this._readyState = info.readyState;

    this._bufferedAmount = 0;
    this._label = info.label;
    this._id = info.id === -1 ? null : info.id; // null until negotiated.
    this._ordered = Boolean(info.ordered);
    this._maxPacketLifeTime = info.maxPacketLifeTime;
    this._maxRetransmits = info.maxRetransmits;
    this._protocol = info.protocol || '';
    this._negotiated = Boolean(info.negotiated);
    this._readyState = info.readyState;

    this._registerEvents();
  }

  get bufferedAmount(): number {
    return this._bufferedAmount;
  }

  get label(): string {
    return this._label;
  }

  get id(): number {
    return this._id;
  }

  get ordered(): boolean {
    return this._ordered;
  }

  get maxPacketLifeTime(): number {
    return this._maxPacketLifeTime;
  }

  get maxRetransmits(): number {
    return this._maxRetransmits;
  }

  get protocol(): string {
    return this._protocol;
  }

  get negotiated(): boolean {
    return this._negotiated;
  }

  get readyState(): string {
    return this._readyState;
  }

  send(data: string | ArrayBuffer | ArrayBufferView) {
    if (typeof data === 'string') {
      WebRTCModule.dataChannelSend(this._peerConnectionId, this._reactTag, data, 'text');
      return;
    }

    get label(): string {
        return this._label;
    }

    get id(): number {
        return this._id;
    }

    get ordered(): boolean {
        return this._ordered;
    }

    get maxPacketLifeTime(): number {
        return this._maxPacketLifeTime;
    }

    get maxRetransmits(): number {
        return this._maxRetransmits;
    }

    get protocol(): string {
        return this._protocol;
    }

    get negotiated(): boolean {
        return this._negotiated;
    }

    get readyState(): string {
        return this._readyState;
    }

    send(data: string | ArrayBuffer | ArrayBufferView) {
        if (typeof data === 'string') {
            WebRTCModule.dataChannelSend(this._peerConnectionId, this._reactTag, data, 'text');
            return;
        }

        // Safely convert the buffer object to an Uint8Array for base64-encoding
        if (ArrayBuffer.isView(data)) {
            data = new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
        } else if (data instanceof ArrayBuffer) {
            data = new Uint8Array(data);
        } else {
            throw new TypeError('Data must be either string, ArrayBuffer, or ArrayBufferView');
        }
        WebRTCModule.dataChannelSend(this._peerConnectionId, this._reactTag, base64.fromByteArray(data), 'binary');
    }

    close() {
        if (this._readyState === 'closing' || this._readyState === 'closed') {
            return;
        }
      }),
      EventEmitter.addListener('dataChannelReceiveMessage', ev => {
        if (ev.reactTag !== this._reactTag) {
          return;
        }
        let data = ev.data;
        if (ev.type === 'binary') {
          data = base64.toByteArray(ev.data).buffer;
        }
        this.dispatchEvent(new MessageEvent('message', {data}));
      }),
      EventEmitter.addListener('dataChannelDidChangeBufferedAmount', ev => {
        if (ev.reactTag !== this._reactTag) {
          return;
        }
        this._bufferedAmount = ev.bufferedAmount;
        if (this._bufferedAmount < this.bufferedAmountLowThreshold) {
          this.dispatchEvent(new RTCDataChannelEvent('bufferedamountlow', {channel: this}));
        }
      }),
    ];
  }
}
