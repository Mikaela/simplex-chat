//
//  MarkedDeletedItemView.swift
//  SimpleX (iOS)
//
//  Created by JRoberts on 30.11.2022.
//  Copyright © 2022 SimpleX Chat. All rights reserved.
//

import SwiftUI
import SimpleXChat

struct MarkedDeletedItemView: View {
    @Environment(\.colorScheme) var colorScheme
    var chatItem: ChatItem
    var showMember = false

    var body: some View {
        HStack(alignment: .bottom, spacing: 0) {
            if showMember, let member = chatItem.memberDisplayName {
                Text(member).font(.caption).fontWeight(.medium) + Text(": ").font(.caption)
            }
            if case let .moderated(byGroupMember) = chatItem.meta.itemDeleted {
                markedDeletedText("moderated by \(byGroupMember.chatViewName)")
            } else {
                markedDeletedText("marked deleted")
            }
            CIMetaView(chatItem: chatItem)
                .padding(.horizontal, 12)
        }
        .padding(.leading, 12)
        .padding(.vertical, 6)
        .background(chatItemFrameColor(chatItem, colorScheme))
        .cornerRadius(18)
        .textSelection(.disabled)
    }

    func markedDeletedText(_ s: LocalizedStringKey) -> some View {
        Text(s)
            .font(.caption)
            .foregroundColor(.secondary)
            .italic()
            .lineLimit(1)
    }
}

struct MarkedDeletedItemView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            MarkedDeletedItemView(chatItem: ChatItem.getSample(1, .directSnd, .now, "hello", .sndSent, itemDeleted: .deleted))
        }
        .previewLayout(.fixed(width: 360, height: 200))
    }
}
