
## AI/ML SPECIFIC QUESTIONS
###  DeBERTa Model
* [x] **What is the difference between BERT and DeBERTa?**
    * BERT (**Bidirectional Encoder Representations from Transformers**) and DeBERTa (**Decoding-enhanced BERT with disentangled attention**) are both transformer-based language models, but DeBERTa introduces several key improvements over BERT:

    * **Disentangled Attention Mechanism**: This is DeBERTa's most significant innovation. While BERT represents each
      word as a single vector combining content and position, DeBERTa separates these into two vectors—one for content and one for relative position. The attention weights between tokens are computed using three components: content-to-content, content-to-position, and position-to-content. This allows the model to better capture the nuanced relationships between word meaning and word order.

    * **Enhanced Mask Decoder**: DeBERTa adds an enhanced mask decoder that uses absolute position embeddings just before
      the softmax layer for predicting masked tokens during pre-training. This incorporates absolute position information where it matters most, complementing the relative position information used in the attention layers.

    * **Performance**: These architectural changes led to substantial improvements. DeBERTa models typically outperform
      BERT on various NLP benchmarks like GLUE, SuperGLUE, and SQuAD, often achieving better results with similar or smaller model sizes.

    * **Training Efficiency**: The disentangled attention helps DeBERTa learn more efficiently from pre-training data,
      generally requiring less data or fewer parameters to achieve comparable or better performance than BERT.

    * In practice, DeBERTa has become a popular choice when you need strong performance on language understanding tasks,
      though BERT remains widely used due to its simplicity, earlier establishment in the field, and the extensive ecosystem built around it.

    * **Concrete examples:**

        * **Content and Position Vectors**

          Imagine the sentence: "**The cat sat on the mat**"

            * **BERT's approach**: Each word gets one vector that mixes meaning and location together:
                - "cat" → [0.2, 0.5, 0.8, ...] (this single vector contains both what "cat" means AND that it's in position 2)

            * **DeBERTa's approach**: Each word gets TWO separate vectors:
                - "cat" content → [0.2, 0.5, 0.8, ...] (what the word means)
                - "cat" position → [0.1, 0.0, 0.3, ...] (where it appears in the sentence)

            * This separation helps because "cat" means the same thing whether it's at the beginning or end of a sentence,
              but its relationship to other words changes based on position.

        * **Attention Weights**
            * Think of attention weights as "how much should I focus on each word?"

            * When processing "sat", the model looks at all other words:
                - "The" → weight: 0.1 (not very important for understanding "sat")
                - "cat" → weight: 0.7 (very important! who is sitting?)
                - "on" → weight: 0.5 (important! where is the sitting happening?)
                - "mat" → weight: 0.3 (somewhat important)

            * These weights are numbers between 0 and 1 that determine how much each word influences the understanding of
              "sat".

        * **Masking**

          During training, the model plays a "fill in the blank" game:

            * **Original**: "The cat sat on the mat"
            * **Masked**: "The cat [MASK] on the mat"

            * The model must predict that [MASK] should be "sat". This teaches it to understand language context.

            * **Another example**: "I love eating [MASK]" → could be "pizza", "apples", "chocolate". The model learns what
              words make sense in different contexts.

        * **Softmax**
            * Softmax converts raw scores into probabilities that add up to 100%.
            * Say the model is predicting the masked word "The cat [MASK] on the mat":

                * **Before softmax (raw scores)**:
                    - "sat" → 4.2
                    - "jumped" → 2.1
                    - "ran" → 1.8
                    - "ate" → 0.5

                * **After softmax (probabilities)**:
                    - "sat" → 75%
                    - "jumped" → 15%
                    - "ran" → 8%
                    - "ate" → 2%

            * Now the model can say: "I'm 75% confident the word is 'sat'". The softmax ensures all probabilities are
              positive and sum to 100%.

    * **How DeBERTa Uses These Together**
        * Let's see DeBERTa process: "The cat sat on the mat" when predicting the masked word "sat":

            1. **Separate vectors**: "cat" has its meaning vector (furry animal) separate from its position vector (word #2)

            2. **Three-way attention**: When looking at "cat" to predict [MASK]:
                - Content-to-content: Does "cat" semantically relate to sitting? ✓
                - Content-to-position: Is position #2 relevant to position #3? ✓
                - Position-to-content: Does position #3 typically follow animal words? ✓

            3. **Enhanced decoder**: Just before the final prediction, DeBERTa adds absolute position info: "This masked word is exactly at position 3 in the sentence"

            4. **Softmax**: Converts final scores to probabilities and predicts "sat" with high confidence

    * This architecture allows DeBERTa to better understand that "cat sat" and "sat cat" have the same words but
      completely different meanings because of their positions.
---
* [x] **Why did you choose DeBERTa over BERT or RoBERTa for email classification?**
    * I built a **two-level hierarchical email classification system** for a multi-sector customer support platform.

        * **Level 1 (Email Type)**:
            - spam
            - query
            - complaint
            - suggestion

        * **Level 2 (Subtype - Sector-Specific)**:
            * For example, in **digital_healthcare**:
                - complaint → account_login_complaint, billing_complaint, app_malfunction_complaint, data_privacy_complaint, etc.
                - query → feature_inquiry, pricing_inquiry, technical_support_query, etc.

            * For **ecommerce**:
                - complaint → delivery_delay_complaint, product_quality_complaint, refund_complaint
                - query → order_status_query, product_availability_query


---

* [x] **Why I Chose DeBERTa Over BERT and LLaMA**

    * **1. Comparison Testing I Performed**

  I tested three architectures on the same dataset:

  | Model | Architecture | Parameters | Test Type Acc | Test Subtype Acc | Inference Time | Memory |
    |-------|-------------|------------|---------------|------------------|----------------|---------|
  | BERT-base | Encoder-only | 110M | 86.3% | 79.5% | ~25ms/email | 6GB |
  | **DeBERTa-v3-base** | Encoder-only | 183M | **91.2%** | **85.8%** | ~35ms/email | 7GB |
  | LLaMA-7B (considered) | Decoder-only | 7B | Not tested | Not tested | ~1500ms/email | 28GB+ |

    * **Why DeBERTa Beat BERT:**

        * **Disentangled Attention for Email Context:**

      Emails have critical position-dependent meanings:

  ```
  Email 1: "I don't want to cancel, but how do I pause my subscription?"
           → Query (asking how to do something)
  
  Email 2: "I want to cancel my subscription immediately"
           → Complaint (expressing dissatisfaction/intent to leave)
  
  BERT sees: "cancel" + "subscription" in both
  BERT attention: Mixed position + content
  Result: Both often classified as 'complaint' (negative keywords dominate)
  
  Email 1 Analysis:
  (1) H_pause · H_how = 0.8     # Content: "pause" + "how" = information seeking
  (2) H_pause · P_question = 0.9 # "pause" in question context = query signal
  (3) P_question · H_pause = 0.7 # Question position expects info-seeking words
  → Correctly classified: Query
  
  Email 2 Analysis:
  (1) H_cancel · H_immediately = 0.8  # Content: urgency + action
  (2) H_cancel · P_demand = 0.9       # "cancel" in demand position = complaint
  (3) P_demand · H_cancel = 0.8       # Demand position expects action verbs
  → Correctly classified: Complaint

  Email 1: "How do I cancel if I need to?"
  → Query (hypothetical, asking about process)
  
  Email 2: "Cancel my account now, your service is terrible"
  → Complaint (expressing dissatisfaction + demanding action)
  
  Email 3: "I'd like to suggest a pause feature instead of cancellation"
  → Suggestion (proposing improvement)

  ```

    * DeBERTa's separated content and position vectors helped it understand:
        - **Content**: 'cancel', 'subscription' (same in both)
        - **Position**: 'don't want' vs 'want' (negation position matters)


* **Better Subtype Discrimination:**

  For digital_healthcare complaints:
    ```
    "Login fails" → account_login_complaint (91% confidence)
    "Charged twice" → billing_complaint (89% confidence)  
    "App crashes" → app_malfunction_complaint (93% confidence)

    BERT often confused billing_complaint with subscription_complaint
    DeBERTa maintained clear boundaries due to enhanced mask decoder
    ```
---
* [x] **Why NOT LLaMA:**

    1. **Resource Constraints**:
        - My production environment: Single T4 GPU (16GB)
        - LLaMA 7B minimum: 28GB GPU memory
        - Would need expensive A100 GPUs

    2. **Inference Speed Critical**:
        - Production requirement: Classify 1000+ emails/minute
        - DeBERTa: ~35ms → Can handle 28 emails/second
        - LLaMA 7B: ~1500ms → Only 0.6 emails/second
        - Would need 40x more infrastructure cost

    3. **Classification vs Generation**:
        - My task: Simple mapping → {type, subtype}
        - LLaMA designed for: Text generation, conversational AI
        - Using LLaMA = using a Formula 1 car for grocery shopping

    4. **Fine-tuning Complexity**:
       ```python
       # DeBERTa: Clean implementation
       model = DebertaV2ForSequenceClassification.from_pretrained(
           'microsoft/deberta-v3-base', 
           num_labels=4
       )
     
       # LLaMA would need:
       # - LoRA adapters
       # - 4-bit quantization  
       # - Custom prompt templates
       # - 10x more code complexity
       ```

---

* [x] "Walk me through how you trained and optimized the model."**
    * I ran **systematic experiments** changing one variable at a time:

        1.  **Experiment 1: Learning Rate Tuning**

       ```python
        Learning Rates Tested: [1e-5, 2e-5, 3e-5, 5e-5]
  
        Results:
        - 1e-5: Type Acc: 88.1%, Subtype Acc: 81.3% (underfit, slow convergence)
        - 2e-5: Type Acc: 91.2%, Subtype Acc: 85.8% ✓ SELECTED
        - 3e-5: Type Acc: 90.8%, Subtype Acc: 84.9% (slight instability)
        - 5e-5: Type Acc: 89.4%, Subtype Acc: 82.1% (overfit early, unstable)
       ```

  **Why 2e-5?** Balanced convergence speed with stability. Higher rates caused loss spikes in later epochs.

    2. **Experiment 2: Epochs & Early Stopping**

        ```python
        Configurations:
        A) 3 epochs, no early stopping → Type: 88.7%, Subtype: 83.2%
        B) 5 epochs, patience=2 → Type: 90.1%, Subtype: 84.5%
        C) 10 epochs, patience=3 → Type: 91.2%, Subtype: 85.8% ✓ SELECTED
        D) 15 epochs, patience=5 → Type: 91.4%, Subtype: 86.1% (overfitting on train)
        ```

    * **Why 10 epochs + patience=3?**
        - Gave model enough time to learn complex subtype patterns
        - Early stopping prevented overfitting (stopped at epoch 7 in final run)
        - Config D showed 99.2% train accuracy but only 91.4% test → clear overfitting

    3. **Experiment 3: Batch Size Impact**

  ```python
  Batch Sizes: [4, 8, 16, 32]
  
  Results:
  - 4: Slower training (6 hours), Type: 90.8%, Subtype: 85.1%
  - 8: 3.5 hours, Type: 91.2%, Subtype: 85.8% ✓ SELECTED
  - 16: 2.5 hours, Type: 90.3%, Subtype: 84.2% (worse generalization)
  - 32: 2 hours, Type: 89.1%, Subtype: 82.9% (too large, poor convergence)
  ```

    * **Why batch_size=8?** Sweet spot between training speed and gradient quality. Larger batches converged to worse
      local minima.

    4.  **Experiment 4: Learning Rate Schedulers**

  ```python
  Schedulers Tested:
  A) Constant LR → Type: 89.7%, Subtype: 83.4%
  B) Linear decay → Type: 90.5%, Subtype: 84.6%  
  C) Cosine with warmup → Type: 91.2%, Subtype: 85.8% ✓ SELECTED
  D) Polynomial decay → Type: 90.1%, Subtype: 83.9%
  ```

    * **Why Cosine + Warmup?**
  ```python
  lr_scheduler = get_cosine_schedule_with_warmup(
      optimizer=torch.optim.AdamW(model.parameters(), lr=2e-5),
      num_warmup_steps=50,  # Gentle start prevents early instability
      num_training_steps=max_train_steps,
  )
  ```
    - Warmup (50 steps): Prevented destructive early updates on pre-trained weights
    - Cosine decay: Smooth learning rate reduction allowed fine-grained optimization in later epochs

    5. **Experiment 5: Weight Decay (Regularization)**

  ```python
  Weight Decay Values: [0, 0.001, 0.01, 0.1]
  
  Results:
  - 0: Type: 92.1%, Subtype: 86.9% (99.8% train → overfitting)
  - 0.001: Type: 91.5%, Subtype: 86.2%
  - 0.01: Type: 91.2%, Subtype: 85.8% ✓ SELECTED
  - 0.1: Type: 88.3%, Subtype: 81.2% (too much regularization)
  ```

    * **Why 0.01?** Prevented overfitting without sacrificing too much capacity. Config with 0 weight decay had
      train-test gap of 13%.

---

* [x] Why I Didn't Pursue 100% Accuracy

    * This is a critical point. I **deliberately avoided configurations that achieved very high training accuracy**.
      Here's why:

    * **The Overfitting Trap**

  ```
  Configuration D (rejected):
  - 15 epochs, weight_decay=0, batch_size=4
  - Training: Type 99.2%, Subtype 97.8%
  - Testing: Type 91.4%, Subtype 86.1%
  - Train-test gap: 7.8% for type, 11.7% for subtype → OVERFITTING
  
  Configuration Selected:
  - 10 epochs, weight_decay=0.01, batch_size=8  
  - Training: Type 94.3%, Subtype 89.1%
  - Testing: Type 91.2%, Subtype 85.8%
  - Train-test gap: 3.1% for type, 3.3% for subtype → HEALTHY
  ```



* **Real-World Production Concerns**

    1. **Email Variance in Production**:
  ```
  Training data: "I want to cancel my subscription"
  Production sees: 
  - "pls cancel my subscriptiom" (typo)
  - "Need 2 cancel ASAP!!!" (informal)
  - "Requesting subscription termination" (formal)

  Overfit model: Fails on variations
  Generalized model: Handles linguistic diversity
  ```

    2. **New Sector Patterns**:
    - Model needs to handle emails from sectors it hasn't seen
    - Overfit model memorizes training sectors exactly
    - My model with 85.8% subtype accuracy **generalizes better** to new patterns

    3. **Label Noise Reality**:
  ```
  Real data issues found:
  - 3-5% annotation errors in training data
  - Ambiguous emails: "I'm confused about billing" 
    (Could be query OR complaint)
  - Pursuing 100% means fitting noise, not signal
  ```

    4. **The 85-90% Accuracy Zone**

    * For text classification tasks:
        - **<80%**: Underfitting, model too simple
        - **85-92%**: **Sweet spot** - captures patterns, generalizes well
        - **>95% on test**: Usually indicates data leakage or overfitting
        - **99% train, 90% test**: Classic overfitting

    * My 91.2% type / 85.8% subtype accuracy is **optimal for production**, not maximal for vanity metrics."

---

* [x]  How did you handle model checkpointing for production?

* **Checkpointing Configuration**

  ```python
  training_args = TrainingArguments(
      eval_strategy="steps",
      eval_steps=20,          # Evaluate every 20 steps
      save_strategy="steps", 
      save_steps=20,          # Save checkpoint every 20 steps
      load_best_model_at_end=True,
      metric_for_best_model="accuracy",
      save_total_limit=3,     # Keep only 3 best checkpoints
  )
  ```

* **Why This Strategy?**

  **1. Frequent Checkpointing (every 20 steps):**

  "My dataset had ~8,000 training emails. With batch_size=8:
  ```
  Steps per epoch = 8000 / 8 = 1000 steps
  Checkpoints per epoch = 1000 / 20 = 50 checkpoints
  
  Total checkpoints across 10 epochs = 500 potential saves
  Kept only top 3 = efficient storage
  ```

  **Benefits:**
    - Captured model at multiple convergence points
    - Protected against training interruptions (GCP preemptible instances)
    - Could resume from any good checkpoint if later epochs degraded

  **2. Best Model Selection:**

  ```python
  load_best_model_at_end=True
  metric_for_best_model="accuracy"
  ```

  The final model wasn't from epoch 10, it was from **epoch 7, step 7340** where validation accuracy peaked:

  ```
  Epoch 7, Step 7340: Val Acc = 91.2% ← BEST (saved)
  Epoch 8, Step 8200: Val Acc = 90.8% (not saved)
  Epoch 9, Step 9100: Val Acc = 90.5% (early stopping triggered)
  ```

  This prevented using an overfit later checkpoint.

    3. **Production Deployment Checkpointing**

  ```python
  # Final model saved with all components
  output_dir = "./deberta_cosine"
  trainer.save_model(output_dir)
  tokenizer.save_pretrained(output_dir)
  
  # Save encoders for production inference
  encoder_dir = './final_model/encoders'
  with open(os.path.join(encoder_dir, 'department_type_encoders.pkl'), 'wb') as f:
      pickle.dump(department_type_encoders, f)
  with open(os.path.join(encoder_dir, 'department_subtype_encoders.pkl'), 'wb') as f:
      pickle.dump(department_subtype_encoders, f)
  with open(os.path.join(encoder_dir, 'department_subtype_hierarchies.pkl'), 'wb') as f:
      pickle.dump(department_subtype_hierarchies, f)
  ```
---
* [x] **Why Save Encoders Separately?**

  In production, for each new email:

  ```python
  # Load once at startup
  model = load_model('./final_model')
  tokenizer = load_tokenizer('./final_model')
  encoders = load_encoders('./final_model/encoders')
  
  # Fast inference (no re-initialization)
  def classify_email(email, department):
      inputs = tokenizer(f"{department} [SEP] {email}", ...)
      outputs = model(**inputs)
      
      # Use department-specific encoder
      type_pred = encoders[department].inverse_transform(...)
      return type_pred, subtype_pred
  ```

  This architecture allows:
    - **Single model load** (not per-sector models)
    - **O(1) inference time** regardless of numbe- **Easy sector addition** (just add new encoder, no retraining)

---

* [x] **How did you measure model performance comprehensively?"**

    * **Multi-Dimensional Testing Approach**

        * **1. Holdout Test Set Evaluation**

          ```python
          # 80-20 stratified split
          train_test_split(test_size=0.2, stratify=numeric_labels, random_state=42)
    
          Metrics Calculated:
          - Overall Accuracy (Type & Subtype)
          - Per-Sector Accuracy
          - Per-Class Precision, Recall, F1
          - Confusion Matrices
          ```

          **Results Example:**

          ```
          Type Classification (4 classes):
                          Precision  Recall  F1-Score  Support
          spam            0.97       0.95    0.96      450
          query           0.89       0.91    0.90      523  
          complaint       0.93       0.92    0.92      612
          suggestion      0.86       0.88    0.87      215
    
          Macro Avg       0.91       0.92    0.91      1800
          Accuracy                            0.912
    
          Subtype Classification (87 total subtypes):
          Macro Avg       0.84       0.86    0.85      1800
          Accuracy                            0.858
          ```

        * **2. Cross-Sector Generalization Testing**
            * Tested model on **held-out sectors**:

          ```python
          # Training: 7 sectors
          train_sectors = ['digital_healthcare', 'ecommerce', 'health_insurance', 
                           'IT', 'retail', 'tourism', 'travel']
    
          # Testing: 2 unseen sectors
          test_sectors = ['hospitality', 'hospitals']
    
          Results:
          Known sectors: Type 91.2%, Subtype 85.8%
          Unseen sectors: Type 87.3%, Subtype 78.9%
          → Degradation: 3.9% type, 6.9% subtype (acceptable generalization)
          ```

        * **3. Confidence Calibration Testing**

            ```python
            def test_confidence_calibration(predictions, true_labels):
                """Measure if confidence scores match actual accuracy"""
    
            Results:
            Confidence 90-100%: Actual accuracy 93.2% (well-calibrated)
            Confidence 70-90%:  Actual accuracy 81.5% (well-calibrated)
            Confidence 50-70%:  Actual accuracy 68.3% (well-calibrated)
            Confidence <50%:    Actual accuracy 43.1% (model knows uncertainty!)
            ```

      **Production Impact:**
      ```python
      # Use confidence thresholding in production
      if subtype_confidence < 0.3:
          return type_only, None  # Escalate to human review
      ```

      This reduced false subtype assignments by 34%.

        * **4. Error Analysis Testing**

          Manual review of 200 misclassified emails revealed:

            ```
            Error Categories:
            1. Ambiguous emails (38%):
               "I have a question about my bill" 
               → Could be query OR complaint
    
            2. Multi-intent emails (27%):
               "I want to cancel (complaint) but first, 
                how does cancellation work? (query)"
    
            3. Annotation errors (18%):
               Training label was wrong, model was right
    
            4. Rare subtype patterns (12%):
               Subtypes with <50 training examples
    
            5. Genuine model errors (5%):
               Clear misclassification
            ```

            * **Actions Taken:**
                - Added data augmentation for rare subtypes
                - Implemented multi-label classification for multi-intent (future work)
                - Corrected annotation errors (improved dataset)

        * **5. Production A/B Testing**

            ```
            Week 1: BERT baseline
            - Accuracy (human-verified): 86.1%
            - Avg classification time: 28ms
            - Human review rate: 23%
    
            Week 2: DeBERTa model  
            - Accuracy (human-verified): 91.2%
            - Avg classification time: 35ms
            - Human review rate: 15% ← 35% reduction!
    
            ROI: 8% less human review = $12k/month savings
            Slightly slower inference (7ms) = negligible impact
            ```

        * **6. Stress Testing**

            ```python
            # Adversarial examples
            test_cases = [
              "asdfghjkl",  # Gibberish
              "..................",  # Special chars only
              "a" * 5000,  # Extreme length
              "",  # Empty email
              "😀😀😀",  # Emoji only
            ]
    
            Model Behavior:
            - Gibberish → spam (91% confidence) ✓
            - Special chars → spam (94% confidence) ✓
            - Extreme length → Truncated to 512 tokens, still classified
            - Empty → Defaulted to 'query' with 23% confidence (flagged for review) ✓
            - Emoji → spam (87% confidence) ✓
            ```

---

* [x] **Summarize your final production model.**

      ```
      Model: DeBERTa-v3-base (microsoft/deberta-v3-base)
      Architecture: Custom 2-level classifier

      Training Configuration:
      - Epochs: 10 (early stopped at 7)
      - Learning rate: 2e-5 with cosine schedule
      - Batch size: 8
      - Weight decay: 0.01
      - Warmup steps: 50

      Performance:
      - Type accuracy: 91.2%
      - Subtype accuracy: 85.8%
      - Inference time: 35ms/email
      - Memory footprint: 7GB GPU / 2GB CPU (ONNX optimized)

      Deployment:
      - Checkpoint: Epoch 7, Step 7340
      - Format: PyTorch + ONNX runtime
      - Scalability: 28 emails/second/GPU
      - Production uptime: 99.7%

      Business Impact:
      - Human review reduction: 35%
      - Cost savings: $12k/month
      - Customer satisfaction: +4.2% (faster routing)
      ```



* **If I could summarize my approach in three principles:**

    1. **Systematic Experimentation**: I tested 5 variables × 3-4 values each = 15-20 configurations systematically, not randomly.

    2. **Generalization Over Memorization**: I chose 91% test accuracy over 99% train accuracy because production demands robustness, not perfection.

    3. **Production-First Design**: Every decision (checkpointing frequency, confidence thresholding, encoder separation) was made thinking about deployment, not just training metrics.

* **DeBERTa wasn't just better than BERT—it was optimal for my constraints**: better accuracy than BERT, practical
  resource needs unlike LLaMA, and robust generalization for real-world email variance."


* [x] **Explain disentangled attention mechanism in DeBERTa**
    * The Core Difference from BERT

      **BERT's Problem:**
        ```python
        # Single vector mixes everything
        "cat" at position 2 = [meaning + position] = [0.9, 0.5, 0.5, 1.0]
  
        Problem: "cat" at position 2 ≠ "cat" at position 6
        (Same word, different vectors because position is baked in)
        ```

      **DeBERTa's Solution:**
        ```python
        # Two separate vectors
        "cat" content: H = [0.8, 0.3, 0.5, 0.9]  # Same everywhere
        position 2:    P = [0.1, 0.2, 0.0, 0.1]  # Position-specific
  
        Now "cat" has the same content vector regardless of position!
        ```

    * Three-Way Attention Calculation

  Instead of BERT's single attention score, DeBERTa computes **three separate scores**:

    ```python
    # Attention from word i to word j
    Total_Attention(i,j) = (1) + (2) + (3)

    (1) H_i · H_j   # Content-to-Content: "Are these words related?"
    (2) H_i · P_j   # Content-to-Position: "Does this word fit this position?"
    (3) P_i · H_j   # Position-to-Content: "What content appears at this distance?"
    ```


* Why This Matters - Real Example

  ```
  Email 1: "I don't want to cancel"  → Query
  Email 2: "I want to cancel"        → Complaint

  Word "cancel" appears in both, but different positions relative to "want"
  ```

  **BERT:**
    - Mixes everything together
    - Struggles to distinguish the two

  **DeBERTa:**
  ```python
  (1) H_cancel · H_want = 0.7  # Same in both emails (content similarity)

  (2) H_cancel · P_after_want = 0.9   # Email 2: "want cancel" - strong complaint signal
      H_cancel · P_after_dont_want = 0.2  # Email 1: "don't want cancel" - weak

  (3) P_cancel · H_want = 0.6  # Position relationship matters

  Result: DeBERTa correctly distinguishes query vs complaint
  ```



* Visual Comparison

  **BERT Attention:**
  ```
  "cat" → "sat"
  Single score: 0.75 (everything mixed)
  ```

  **DeBERTa Attention:**
  ```
  "cat" → "sat"
  Content similarity:     0.70  (semantic relation)
  Content→Position:       0.15  (nouns don't strongly predict verb positions)
  Position→Content:       0.20  (subject position expects verb nearby)
  Total:                  1.05  (more nuanced understanding)
  ```



* Key Benefits

    1. **Position Independence**: "cancel" means the same thing everywhere
    2. **Better Negation Handling**: Distinguishes "want" vs "don't want" via position
    3. **Relative Positioning**: Understands "word A typically appears 2 positions before word B"
    4. **Improved Accuracy**: 3-5% better on classification tasks



* In Your Email Classification

  ```python
  Email: "I'm having trouble logging into my account"

  DeBERTa separates:
  - Content: "trouble", "logging", "account" → complaint indicators
  - Position: "I'm having" at start → personal issue structure
  - Relationship: "trouble" near "logging" → technical complaint

  Result: Correctly predicts complaint → account_login_complaint
  ```

* **Bottom Line:** DeBERTa asks "WHAT does this word mean?" and "WHERE is it?" separately, then combines the answers intelligently. BERT mixes them from the start.

---
* [x] **What approach did you use to train the email classification model?**
    * Fine-tuned DeBERTa-v3-base for hierarchical classification with custom architecture:
        * **Level 1:** 4 email types (spam, query, complaint, suggestion)
        * **Level 2**: Department-specific subtypes (87 total across 9 sectors)
        * Custom dual classifier heads on top of DeBERTa encoder
        * Combined loss function: type_loss + subtype_loss
---

* [x] **How did you prepare training data for email classification?**
    *  **Data preparation steps:**
        1. Loaded 9 CSV files from GCS (9 different sectors)
        2. Combined into single dataset (~8,000 emails)
        3. Created input format: "department [SEP] email_text"
        4. Built department-specific label encoders (LabelEncoder for each sector)
        5. Encoded types and subtypes separately per department
        6. Tokenized with max_length=512, padding and truncation
        7. Created hierarchical mappings: department → type → valid_subtypes

---
* [x] **What evaluation metrics did you use? (Accuracy, Precision, Recall, F1-Score)**
    * **Primary Metrics:**
        - Accuracy: 91.2% (type), 85.8% (subtype)
        - Per-class Precision, Recall, F1-Score

    * **Additional Metrics:**
        - Confusion matrices for each class
        - Per-sector accuracy breakdown
        - Confidence calibration curves
        - Train-test accuracy gap (monitoring overfitting)

    * **Final Results:**
        - Type: Precision=0.91, Recall=0.92, F1=0.91
        - Subtype: Precision=0.84, Recall=0.86, F1=0.85
---
* [x] **How did you handle imbalanced datasets?**
    * **Techniques Used:**

        1. Stratified Split:
           train_test_split(stratify=numeric_labels)  # Preserved class distribution

        2. Class Weights (implicit):
           CrossEntropyLoss handles per-batch imbalance automatically

        3. Data Augmentation (for rare subtypes <50 examples):
            - Paraphrasing
            - Synonym replacement
            - Back-translation

        4. Evaluation Strategy:
            - Used macro-averaged F1 (treats all classes equally)
            - Monitored per-class metrics separately

    * Class Distribution Example:
        - Spam: 450 emails
        - Query: 523 emails
        - Complaint: 612 emails (largest)
        - Suggestion: 215 emails (smallest - augmented)
---
* [x] **Did you use transfer learning or fine-tuning?**
    * Started with pre-trained DeBERTa-v3-base (183M parameters)
      model = DebertaV2ForSequenceClassification.from_pretrained(
      'microsoft/deberta-v3-base'
      )

    * Added custom classification heads
  ```
  self.type_classifier = torch.nn.Linear(hidden_size, max_types)
  self.subtype_classifier = torch.nn.Linear(hidden_size, max_subtypes)
  ```

    * Fine-tuning Strategy:
        - Froze: Nothing (full model fine-tuning)
        - Learning rate: 2e-5 (small to preserve pre-trained weights)
        - Warmup: 50 steps (gradual unfreezing effect)
        - Epochs: 10 (early stopped at 7)

    * Why full fine-tuning:
        - Dataset large enough (8,000 emails)
        - Domain-specific patterns (customer support emails)
        - Better performance than frozen encoder approach
---

* [x] **What was your dataset size and how did you split it?**
    * Dataset Size:
        - Total: ~8,000 emails across 9 sectors
        - Per sector: 800-1,000 emails
        - 4 types × 87 subtypes (department-specific)

      Split Strategy:
        - Train: 80% (6,400 emails)
        - Test: 20% (1,600 emails)
        - Method: Stratified random split (random_state=42)
        - Stratification: By numeric_labels (preserved type distribution)

      Additional Validation:
        - Held-out sectors: 2 sectors completely unseen during training
        - Cross-sector test: 87.3% type, 78.9% subtype accuracy

      No separate validation set:
        - Used eval_steps=20 on test set during training
        - Early stopping based on test accuracy (patience=3)

###  RAG (Retrieval-Augmented Generation)


* [x] **What is RAG and why did you use it for automated email responses?**

    * **RAG (Retrieval-Augmented Generation)** = Retrieve relevant documents + Generate response using LLM

    * **Why we used it:**
        - Customer emails reference company-specific policies, products, FAQs
        - LLMs don't know our internal documentation
        - RAG grounds responses in actual company knowledge
        - Prevents hallucinations - responses based on retrieved docs, not made up
        - Reduces need to fine-tune expensive LLMs

    * **Example:**
      ```
      Customer: "What's your refund policy for annual subscriptions?"
  
      Without RAG: LLM guesses (wrong/generic answer)
      With RAG: 
        1. Retrieve: refund_policy.pdf from our docs
        2. Generate: Accurate answer citing our 30-day policy
      ```

---

* [x] **How did you implement RAG architecture?**

    *

      ```python
      # RAG Pipeline Flow:

      1. Document Ingestion (one-time):
         - Upload PDFs, docs, FAQs to system
         - Split into chunks (512 tokens each)
         - Generate embeddings using sentence-transformers
         - Store in Elasticsearch vector index

      2. Query Processing (runtime):
         email = "What's your refund policy?"

         # Step 1: Retrieve
         query_embedding = embed(email)
         relevant_docs = elasticsearch.knn_search(
             query_embedding, 
             k=5  # Top 5 most relevant chunks
         )

         # Step 2: Generate prompt
         context = "\n".join(relevant_docs)
         prompt = f"""
         Context: {context}

         Customer Question: {email}

         Generate professional response based only on context above.
         """

         # Step 3: Generate response
         response = mistral_api.generate(prompt)

         return response
      ```

    * **Architecture:**
  ```
  Customer Email → Embedding Model → Vector Search (Elasticsearch) 
  → Top-K Docs → Prompt Template → LLM (Mistral/GPT) → Response
  ```

---

* [x] What vector database or search engine did you use for retrieval?

    * **Elasticsearch with k-NN (vector search)**

          **Configuration:**
          ```python
          # Index settings
          {
            "mappings": {
              "properties": {
                "content": {"type": "text"},
                "embedding": {
                  "type": "dense_vector",
                  "dims": 768,  # Sentence-transformer dimension
                  "index": true,
                  "similarity": "cosine"
                },
                "metadata": {
                  "department": {"type": "keyword"},
                  "doc_type": {"type": "keyword"}
                }
              }
            }
          }

          # Search query
          elasticsearch.search(
            index="company_docs",
            knn={
              "field": "embedding",
              "query_vector": email_embedding,
              "k": 5,
              "num_candidates": 50
            },
            filter={"department": "billing"}  # Optional filtering
          )
          ```

    * **Why Elasticsearch:**
        - Already using it for log search
        - Built-in vector search (k-NN)
        - Hybrid search: combine vector + keyword filters
        - Fast retrieval (<50ms for 100k documents)
        - Easy integration with existing infrastructure

---

* [x] **How did you create embeddings?**

    * **Model:** `sentence-transformers/paraphrase-multilingual-mpnet-base-v2`

      **Implementation:**
      ```python
      from sentence_transformers import SentenceTransformer
  
      # Load once at startup
      embedding_model = SentenceTransformer('paraphrase-multilingual-mpnet-base-v2')
  
      # Document ingestion
      def index_document(doc_text):
          chunks = split_into_chunks(doc_text, max_length=512)
          for chunk in chunks:
              embedding = embedding_model.encode(chunk)  # 768-dim vector
              elasticsearch.index(
                  index="company_docs",
                  body={
                      "content": chunk,
                      "embedding": embedding.tolist()
                  }
              )
  
      # Query embedding (same model for consistency)
      def search(email):
          query_embedding = embedding_model.encode(email)
          results = elasticsearch.knn_search(query_embedding)
          return results
      ```

    * **I used paraphrase-multilingual-mpnet-base-v2 because:**

        - Semantic matching: Trained specifically on paraphrase tasks - perfect for matching customer questions to
          documentation that's worded differently
        - Multilingual support: Handles 50+ languages in same embedding space - future-proofs system if we get non-English emails
        - Quality vs speed tradeoff: 25ms encoding is acceptable for RAG pipeline; quality improvement over faster models is worth it
        - Production proven: 768 dimensions provide rich representations without being as expensive as OpenAI embeddings"


---

* [x] **How to handle context window limitations?**

    * **Strategies used:**

      **1. Chunk documents intelligently:**
      ```python
      # Split by semantic boundaries, not fixed tokens
      def smart_chunk(document, max_tokens=512):
          # Split on: paragraphs → sentences → tokens
          paragraphs = document.split('\n\n')
          chunks = []
          current_chunk = ""
  
          for para in paragraphs:
              if len(tokenize(current_chunk + para)) < max_tokens:
                  current_chunk += para + "\n\n"
              else:
                  chunks.append(current_chunk)
                  current_chunk = para
  
          return chunks
      ```

      **2. Retrieve top-K most relevant chunks only:**
      ```python
      # Not all docs, just most relevant
      k = 5  # Typically 5 chunks × 512 tokens = 2560 tokens
      # Leaves room for: prompt template + question + response
      ```

      **3. Context window allocation:**
      ```python
      # For Mistral-7B (8k context window):
      Retrieved context: 2500 tokens (top-5 chunks)
      Prompt template:   300 tokens
      User question:     200 tokens
      Response budget:   5000 tokens
      Total:            8000 tokens ✓
  
      # If exceeds limit:
      if total_tokens > 8000:
          # Re-rank and keep only top-3 chunks
          chunks = rerank(chunks, query)[:3]
      ```

      **4. Fallback for long queries:**
      ```python
      if len(email) > 1000:
          # Summarize email first
          summary = mistral_api.summarize(email)
          query_embedding = embed(summary)
      else:
          query_embedding = embed(email)
      ```

      **5. Chunk overlap:**
      ```python
      # 10% overlap between chunks to avoid splitting context
      chunk_1 = doc[0:512]
      chunk_2 = doc[460:972]  # 52 token overlap
      ```

---

###  ML Operations

* [x] **How did you deploy ML models in production?**

    * **Deployment Setup: Azure VM with systemd service**

      ```bash
      # Infrastructure
      - Platform: Azure VM (Standard_D4s_v3: 4 vCPUs, 16GB RAM)
      - OS: Ubuntu 20.04 LTS
      - GPU: Not used (CPU inference sufficient for our load)
  
      # Deployment Steps:
  
      1. Model artifacts on VM:
         /opt/email-classifier/
         ├── models/
         │   ├── deberta_final/          # DeBERTa classification model
         │   ├── embeddings/              # paraphrase-mpnet-base-v2
         │   └── encoders/                # Label encoders (pickle files)
         ├── app.py                       # Flask API
         ├── requirements.txt
         └── config.yaml
  
      2. Python service created:
         /etc/systemd/system/email-classifier.service
  
      3. Service definition:
      [Unit]
      Description=Email Classification Service
      After=network.target
  
      [Service]
      Type=simple
      User=mlops
      WorkingDirectory=/opt/email-classifier
      Environment="PATH=/opt/email-classifier/venv/bin"
      ExecStart=/opt/email-classifier/venv/bin/python app.py
      Restart=always
      RestartSec=10
  
      [Install]
      WantedBy=multi-user.target
  
      4. Start service:
      sudo systemctl enable email-classifier
      sudo systemctl start email-classifier
  
      5. Logging via Filebeat:
      # /etc/filebeat/filebeat.yml
      filebeat.inputs:
      - type: log
        enabled: true
        paths:
          - /var/log/email-classifier/*.log
        json.keys_under_root: true
  
      output.elasticsearch:
        hosts: ["elasticsearch:9200"]
        index: "email-classifier-logs-%{+yyyy.MM.dd}"
  
      6. Monitoring via Prometheus exporter:
      # app.py exposes /metrics endpoint
      from prometheus_client import Counter, Histogram, generate_latest
  
      classification_counter = Counter('emails_classified_total', 'Total emails')
      latency_histogram = Histogram('classification_latency_seconds', 'Latency')
      ```

      **API Endpoint:**
      ```python
      # Flask app running on VM
      from flask import Flask, request, jsonify
      import torch
      from transformers import DebertaV2Tokenizer, DebertaV2ForSequenceClassification
  
      app = Flask(__name__)
  
      # Load model once at startup
      model = load_model('/opt/email-classifier/models/deberta_final')
      tokenizer = load_tokenizer('/opt/email-classifier/models/deberta_final')
  
      @app.route('/classify', methods=['POST'])
      def classify():
          email = request.json['email']
          department = request.json['department']
  
          # Classification
          type_pred, subtype_pred = predict(email, department, model, tokenizer)
  
          # Log to file (Filebeat picks up)
          logger.info({
              "timestamp": datetime.now(),
              "email_length": len(email),
              "department": department,
              "predicted_type": type_pred,
              "predicted_subtype": subtype_pred,
              "latency_ms": inference_time
          })
  
          return jsonify({"type": type_pred, "subtype": subtype_pred})
  
      if __name__ == '__main__':
          app.run(host='0.0.0.0', port=5000)
      ```

      **Why this approach:**
        - Simple, maintainable
        - systemd handles auto-restart on crashes
        - Filebeat for centralized logging
        - Easy rollback (swap model directory)

---

* [x] **How did you version ML models?**

    * **Versioning Strategy:**

      ```bash
      # Directory structure
      /opt/email-classifier/models/
      ├── deberta_v1.0_20240115/       # Initial production model
      ├── deberta_v1.1_20240203/       # Retrained with more data
      ├── deberta_v1.2_20240228/       # Current production
      └── current -> deberta_v1.2_20240228/  # Symlink for easy switching
  
      # Metadata file per version
      /opt/email-classifier/models/deberta_v1.2_20240228/model_metadata.json
      {
        "version": "1.2",
        "trained_date": "2024-02-28",
        "training_data_size": 12000,
        "metrics": {
          "type_accuracy": 0.912,
          "subtype_accuracy": 0.858
        },
        "hyperparameters": {
          "learning_rate": 2e-5,
          "epochs": 10,
          "batch_size": 8
        },
        "git_commit": "a3f5c91",
        "trained_by": "data-team"
      }
      ```

      **Version tracking in code:**
      ```python
      # app.py
      MODEL_VERSION = os.environ.get('MODEL_VERSION', 'current')
      model_path = f'/opt/email-classifier/models/{MODEL_VERSION}'
  
      # Every prediction logs model version
      logger.info({
          "model_version": MODEL_VERSION,
          "prediction": result
      })
      ```

      **Git for code versioning:**
      ```bash
      # Code repo structure
      email-classifier-service/
      ├── .git/
      ├── app.py
      ├── requirements.txt
      ├── deployment/
      │   ├── email-classifier.service
      │   └── deploy.sh
      └── models/
          └── .gitignore  # Don't commit model binaries to git
      ```

      **Model registry (manual):**
      ```
      # Google Sheet tracking:
      Version | Date       | Accuracy | Data Size | Status     | Notes
      1.0     | 2024-01-15 | 0.863    | 8000      | Deprecated | Initial
      1.1     | 2024-02-03 | 0.891    | 10000     | Deprecated | More data
      1.2     | 2024-02-28 | 0.912    | 12000     | Production | Current best
      1.3     | 2024-03-10 | 0.918    | 15000     | Testing    | A/B test 10%
      ```

      **Rollback process:**
      ```bash
      # Switch to previous version
      sudo systemctl stop email-classifier
      ln -sfn deberta_v1.1_20240203 current
      sudo systemctl start email-classifier
  
      # Takes ~30 seconds total
      ```

---

* [x] **How to monitor ML model performance?**

    * **3-Layer Monitoring System:**

        * **Layer 1: Technical Metrics (Prometheus + Grafana)**

      ```python
      # Prometheus metrics exposed at /metrics
      from prometheus_client import Counter, Histogram, Gauge
  
      # Request metrics
      emails_classified = Counter('emails_classified_total', 
                                  'Total emails classified',
                                  ['department', 'predicted_type'])
  
      classification_latency = Histogram('classification_latency_seconds',
                                        'Time to classify email')
  
      model_confidence = Histogram('model_confidence_score',
                                  'Confidence score distribution',
                                  ['prediction_type'])
  
      error_counter = Counter('classification_errors_total',
                             'Total classification errors')
  
      # System metrics
      gpu_memory = Gauge('gpu_memory_used_bytes', 'GPU memory usage')
      cpu_usage = Gauge('cpu_usage_percent', 'CPU usage')
      ```

      **Grafana Dashboard:**
      ```
      Panel 1: Requests/minute (Counter)
      Panel 2: P50, P95, P99 latency (Histogram)
      Panel 3: Error rate (Counter / Total)
      Panel 4: Confidence score distribution (Histogram)
      Panel 5: CPU/Memory usage (Gauge)
      Panel 6: Predictions by department (Counter with labels)
      ```

        * **Layer 2: Business Metrics (Elasticsearch + Kibana)**

      ```python
      # Logged via Filebeat to Elasticsearch
      logger.info({
          "timestamp": datetime.now(),
          "email_id": email_id,
          "department": department,
          "predicted_type": type_pred,
          "predicted_subtype": subtype_pred,
          "confidence_type": 0.91,
          "confidence_subtype": 0.73,
          "latency_ms": 35,
          "model_version": "1.2"
      })
      ```

      **Kibana Dashboards:**
      ```
      1. Daily classification volume by type
      2. Confidence score trends (detect model degradation)
      3. Low-confidence predictions (< 0.5) for human review
      4. Department-wise accuracy (if feedback available)
      5. Error logs and exceptions
      ```

        * **Layer 3: Model Accuracy Monitoring (Weekly Manual Review)**

      ```python
      # Sample 100 random predictions weekly
      # Human reviewers verify correctness
  
      def weekly_accuracy_check():
          # Pull 100 random predictions from last week
          sample = elasticsearch.search(
              index="email-classifier-logs-*",
              size=100,
              query={"match_all": {}},
              sort={"_random": {}}
          )
  
          # Send to human reviewers
          results = []
          for pred in sample:
              is_correct = human_review(pred)
              results.append(is_correct)
  
          accuracy = sum(results) / len(results)
  
          # Alert if accuracy drops
          if accuracy < 0.85:
              send_alert("Model accuracy dropped to {accuracy}")
  
          # Log to tracking sheet
          log_to_sheet(date, accuracy, model_version)
      ```

      **Alerts configured:**
      ```yaml
      # Prometheus alerts
      - alert: HighErrorRate
        expr: rate(classification_errors_total[5m]) > 0.05
        annotations:
          summary: "Error rate above 5%"
  
      - alert: HighLatency
        expr: classification_latency_seconds{quantile="0.95"} > 0.5
        annotations:
          summary: "P95 latency above 500ms"
  
      - alert: LowConfidence
        expr: avg(model_confidence_score) < 0.6
        annotations:
          summary: "Average confidence dropped below 60%"
      ```

---

* [x] **How did you handle model retraining?**

    * **Retraining Strategy: Scheduled + Triggered**

      ### **Scheduled Retraining (Monthly)**

      ```bash
      # Cron job on training VM
      # /etc/cron.d/model-retrain
      0 2 1 * * /opt/scripts/retrain_model.sh
  
      # retrain_model.sh
      #!/bin/bash
      set -e
  
      echo "Starting monthly retraining: $(date)"
  
      # 1. Fetch new labeled data from database
      python fetch_training_data.py --start-date $(date -d "last month" +%Y-%m-01)
  
      # 2. Combine with existing training data
      python combine_datasets.py
  
      # 3. Train new model
      python train.py \
        --config configs/production.yaml \
        --output-dir models/deberta_v$(date +%Y%m%d)
  
      # 4. Evaluate on held-out test set
      python evaluate.py \
        --model models/deberta_v$(date +%Y%m%d) \
        --test-data data/test_set.csv
  
      # 5. If accuracy > current model, deploy
      python compare_and_deploy.py
  
      echo "Retraining completed: $(date)"
      ```

      ### **Triggered Retraining (On-demand)**

      **Trigger conditions:**
        1. Weekly accuracy check drops below 85%
        2. New department/sector added
        3. Significant data drift detected
        4. Manual request from team

      ```python
      # monitor.py - runs daily
      def check_retraining_needed():
          # Check 1: Accuracy degradation
          recent_accuracy = get_weekly_accuracy()
          if recent_accuracy < 0.85:
              trigger_retraining(reason="accuracy_drop")
  
          # Check 2: Data distribution shift
          current_dist = get_prediction_distribution(days=7)
          baseline_dist = get_baseline_distribution()
          kl_divergence = calculate_kl(current_dist, baseline_dist)
  
          if kl_divergence > 0.3:
              trigger_retraining(reason="data_drift")
  
          # Check 3: Low confidence predictions spike
          low_conf_rate = get_low_confidence_rate(threshold=0.5)
          if low_conf_rate > 0.15:
              trigger_retraining(reason="low_confidence")
      ```

      ### **Retraining Process**

      ```python
      # fetch_training_data.py
      def fetch_new_training_data():
          # Get emails from production with human feedback
          query = """
              SELECT email, department, human_verified_type, human_verified_subtype
              FROM email_classifications
              WHERE created_at > last_retrain_date
              AND human_verified = true
          """
  
          new_data = database.execute(query)
  
          # Combine with original training data
          full_dataset = pd.concat([original_data, new_data])
  
          # Remove duplicates
          full_dataset.drop_duplicates(subset=['email'], inplace=True)
  
          return full_dataset
  
      # Training configuration
      config = {
          "learning_rate": 2e-5,
          "epochs": 10,
          "batch_size": 8,
          "early_stopping_patience": 3,
          "dataset_size": len(full_dataset),
          "previous_model_version": current_version
      }
  
      # Train
      new_model = train_deberta(full_dataset, config)
  
      # Evaluate
      test_metrics = evaluate(new_model, test_set)
  
      # Compare with current production model
      if test_metrics['accuracy'] > current_model_accuracy + 0.02:
          # At least 2% improvement required
          deploy_new_model(new_model)
      else:
          log_warning("New model not significantly better, keeping current")
      ```

      **Retraining frequency:**
        - **Scheduled:** Monthly (1st of each month)
        - **Triggered:** As needed (3-4 times/year typically)
        - **Total retrains/year:** ~15-16

---

* [x] **What is A/B testing for ML models?**

    * **A/B Testing = Compare two model versions in production with real traffic**

    * **Our Implementation**

      ```python
      # app.py - A/B testing logic
      import random
  
      MODEL_A_PATH = '/opt/email-classifier/models/deberta_v1.2'  # Current (90% traffic)
      MODEL_B_PATH = '/opt/email-classifier/models/deberta_v1.3'  # New (10% traffic)
  
      model_a = load_model(MODEL_A_PATH)
      model_b = load_model(MODEL_B_PATH)
  
      @app.route('/classify', methods=['POST'])
      def classify():
          email = request.json['email']
          department = request.json['department']
  
          # A/B split: 90% Model A, 10% Model B
          random_value = random.random()
  
          if random_value < 0.9:
              model_version = "A_v1.2"
              prediction = predict(email, department, model_a)
          else:
              model_version = "B_v1.3"
              prediction = predict(email, department, model_b)
  
          # Log which model was used
          logger.info({
              "email_id": email_id,
              "model_version": model_version,
              "prediction": prediction,
              "timestamp": datetime.now()
          })
  
          return jsonify(prediction)
      ```

    * **A/B Test Configuration**

      ```yaml
      # Example test: New model v1.3 vs Current v1.2
      Test Name: "DeBERTa v1.3 with more training data"
      Start Date: 2024-03-10
      Duration: 2 weeks
      Traffic Split: 90% A (v1.2) / 10% B (v1.3)
  
      Success Metrics:
        Primary: Accuracy (human-verified sample)
        Secondary: 
          - Average confidence score
          - P95 latency
          - Error rate
  
      Decision Criteria:
        - Model B accuracy > Model A by 2%
        - Model B latency < 100ms
        - No increase in error rate
      ```

    * **Analysis After 2 Weeks**

      ```python
      # analyze_ab_test.py
      def analyze_results():
          # Fetch predictions from both models
          model_a_preds = elasticsearch.query(
              "model_version:A_v1.2 AND timestamp:[now-2w TO now]"
          )
          model_b_preds = elasticsearch.query(
              "model_version:B_v1.3 AND timestamp:[now-2w TO now]"
          )
  
          # Sample 500 from each for human verification
          sample_a = random.sample(model_a_preds, 500)
          sample_b = random.sample(model_b_preds, 500)
  
          # Human reviewers verify
          accuracy_a = human_verify(sample_a)  # Result: 91.2%
          accuracy_b = human_verify(sample_b)  # Result: 93.1%
  
          # Statistical significance test
          p_value = ttest_ind(accuracy_a, accuracy_b)
  
          results = {
              "Model A (v1.2)": {
                  "accuracy": 0.912,
                  "avg_latency": 35ms,
                  "avg_confidence": 0.84,
                  "sample_size": 45000
              },
              "Model B (v1.3)": {
                  "accuracy": 0.931,  # +1.9% improvement ✓
                  "avg_latency": 38ms,  # +3ms (acceptable)
                  "avg_confidence": 0.87,  # Higher confidence ✓
                  "sample_size": 5000
              },
              "p_value": 0.001,  # Statistically significant
              "decision": "DEPLOY Model B"
          }
  
          return results
      ```

    * **Decision:** Deploy Model B to 100% traffic

    * **Rollout Plan**

  ```
  Week 1: 10% traffic → Collect initial data
  Week 2: 10% traffic → Continue monitoring
  Week 3: Analyze results → Model B wins
  Week 4: 50% traffic → Gradual rollout
  Week 5: 100% traffic → Full deployment
  ```

    * **Why A/B Testing:**
        - **Real-world validation:** Test metrics might not match production
        - **Risk mitigation:** Limit exposure if new model has issues
        - **Statistical confidence:** Prove improvement before full deployment
        - **Detect unexpected issues:** Edge cases only seen in production



* **Summary Table:**

| Question | Answer |
  |----------|--------|
| **Deployment** | Azure VM, Python Flask API as systemd service, Filebeat logging |
| **Versioning** | Directory-based (v1.0, v1.1, v1.2), symlink for current, metadata JSON |
| **Monitoring** | Prometheus metrics → Grafana, Elasticsearch logs → Kibana, Weekly manual review |
| **Retraining** | Monthly scheduled + triggered (accuracy drop, drift), requires 2% improvement |
| **A/B Testing** | 90/10 split, 2-week test, human-verified sample, statistical significance |
---
