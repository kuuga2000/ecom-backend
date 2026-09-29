# Variant selection and carts

A product describes what is sold. A variant describes a specific purchasable choice. Each variant has its own SKU, price, active state, and stock quantity. The `options` array can contain any number of named choices; there is no fixed color/size schema.

For example, a shirt variant can have `color=red` and `size=m`. A fitting variant can have `diameter=mm`, `size=6mm`, and `color=black`. A fourth choice such as `finish=matte` simply adds another `{name,value}` entry. Product variants have unique IDs, and their option combinations are unique within a product.

The cart stores `variant_id` and quantity as one line item. Its database primary key is `(cart_id, variant_id)`, so adding the same variant again increases its quantity while two different variants stay separate. A cart request cannot substitute a product ID or default variant: it must send `variantId` explicitly, even for a simple product.

V6 adds `carts` and `cart_items`. The add and quantity-update operations lock the cart row so two requests for the same cart cannot lose each other's quantity changes. They check that the product and variant are active and that the requested quantity fits the selected variant's stock. The cart stores no price snapshot and reserves no stock; checkout will need a later transaction that rechecks stock and creates an order.

The initial cart is anonymous. The random UUID identifies it and functions as its access token until customer authentication is added. The HTTP examples and responses are in [the cart API reference](../api-docs/04-carts-api.md).
