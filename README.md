# FlashPay 🔦

A flashlight app that somehow decided turning the flashlight off should cost ₹5.

Yep. That's basically the whole idea.

## What is this?

FlashPay is a small Android app I made for the useless project program.

You can turn your flashlight on normally. But when you try to turn it off, the app asks you to pay ₹5.

Obviously, the payment is completely fake. No actual money is involved.

I just thought it would be funny to take one of the simplest features on a phone and put a completely unnecessary paywall in front of it.

## How it works

1. Open the app
2. Turn on the flashlight 🔦
3. Try to turn it off
4. Get hit with a ₹5 payment popup
5. Press **PAY ₹5**
6. The app lets you turn the flashlight off

That's it.

## Demo

▶️ [Watch the FlashPay demo](https://drive.google.com/file/d/1ozvqlgYtAcPgtLbXSBlthKvfoXVFAqjq/view?usp=sharing)

## Built with

- Kotlin
- Jetpack Compose
- Android Camera2 API
- Gradle
- Android Studio

## Important

The ₹5 payment is **not real**.

The app does not connect to:

- UPI
- Google Pay
- Banks
- Cards
- Payment gateways

There is no login, backend, tracking, or anything like that.

The "payment" is just a local button that changes the app's state.

## Privacy

FlashPay doesn't collect or store personal information.

It also doesn't need an internet connection.

## Running it

Clone the repository:

```bash
git clone https://github.com/abhiramtuff/FlashPay.git
